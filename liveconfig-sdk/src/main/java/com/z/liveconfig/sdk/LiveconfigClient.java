package com.z.liveconfig.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Liveconfig 客户端。业务应用只依赖这一个类。
 *
 * 取值路径三层，日志里用 source 区分：
 * ① SERVER   —— 调服务端 /sdk/v1/evaluate，分流计算在服务端完成；
 * ② FALLBACK —— ①超时/失败时用本地快照按同一套算法就地算（保证断网期间不跳变）；
 * ③ CALLER_DEFAULT —— 连快照都没有（首启且服务端不可达），返回调用方给的默认值。
 */
public final class LiveconfigClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LiveconfigClient.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http;
    private final String endpoint;
    private final String sdkKey;
    private final Duration timeout;
    private final FlagContext.InstanceInfo instance;
    private final SnapshotStore store;
    private final SseSubscriber subscriber;
    private final Thread sseThread;

    private LiveconfigClient(Builder b) {
        this.endpoint = trimTrailing(b.endpoint);
        this.sdkKey = b.sdkKey;
        this.timeout = b.timeout;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        this.instance = InstanceIdentity.detect(b.appName, b.publicIpEchoUrl);
        this.store = new SnapshotStore(mapper, http, endpoint, sdkKey, b.fallbackFile);
        this.store.loadFromDisk();
        this.store.refresh();
        if (b.subscribe) {
            this.subscriber = new SseSubscriber(mapper, http, endpoint, sdkKey, store);
            this.sseThread = new Thread(subscriber, "liveconfig-sse");
            this.sseThread.setDaemon(true);
            this.sseThread.start();
        } else {
            this.subscriber = null;
            this.sseThread = null;
        }
        log.info("Liveconfig 已就绪 app={} env={} 本机内网IP={} 兜底configVersion={}",
                b.appName, store.current().envKey(),
                instance == null ? "未知" : instance.privateIp(), store.current().configVersion());
    }

    public static Builder builder() {
        return new Builder();
    }

    // ---------- 类型化取值 ----------

    public boolean getBoolean(String key, String userId, boolean def) {
        return explain(key, FlagContext.ofUser(userId)).booleanValue(def);
    }

    public String getString(String key, String userId, String def) {
        return explain(key, FlagContext.ofUser(userId)).stringValue(def);
    }

    public int getInt(String key, String userId, int def) {
        return explain(key, FlagContext.ofUser(userId)).intValue(def);
    }

    public double getDouble(String key, String userId, double def) {
        return explain(key, FlagContext.ofUser(userId)).doubleValue(def);
    }

    /** JSON 开关返回原始文本，由调用方自己解析成结构 */
    public String getJson(String key, String userId, String def) {
        return explain(key, FlagContext.ofUser(userId)).stringValue(def);
    }

    public boolean isOn(String key, String userId) {
        return getBoolean(key, userId, false);
    }

    /** 拿完整判定过程，控制台的「分流调试」和 demo 的日志都靠它 */
    public FlagDecision explain(String key, FlagContext ctx) {
        FlagContext merged = withInstance(ctx);
        Optional<FlagDecision> server = callServer(key, merged);
        if (server.isPresent()) {
            return server.get();
        }
        Optional<FlagDecision> fallback = FallbackEvaluator.evaluate(mapper, store.current(), key, merged);
        return fallback.orElseGet(() -> FlagDecision.callerDefault(key, null,
                "服务端不可达且本地无该开关快照"));
    }

    public long configVersion() {
        return store.current().configVersion();
    }

    @Override
    public void close() {
        if (subscriber != null) {
            subscriber.stop();
        }
        if (sseThread != null) {
            sseThread.interrupt();
        }
    }

    // ---------- 内部 ----------

    private FlagContext withInstance(FlagContext ctx) {
        if (ctx.instance() != null || instance == null) {
            return ctx;
        }
        return new FlagContext(ctx.userId(), instance, ctx.attributes());
    }

    private Optional<FlagDecision> callServer(String key, FlagContext ctx) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("flagKey", key);
            body.put("userId", ctx.userId());
            body.put("attributes", ctx.attributesOrEmpty());
            if (ctx.instance() != null) {
                Map<String, Object> ins = new LinkedHashMap<>();
                ins.put("privateIp", ctx.instance().privateIp());
                ins.put("publicIp", ctx.instance().publicIp());
                ins.put("appName", ctx.instance().appName());
                body.put("instance", ins);
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint + "/sdk/v1/evaluate"))
                    .header("X-SDK-Key", sdkKey)
                    .header("Content-Type", "application/json")
                    .timeout(timeout)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body),
                            StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                log.warn("evaluate HTTP {}，转本地兜底", response.statusCode());
                return Optional.empty();
            }
            var data = mapper.readTree(response.body()).path("data");
            if (data.isMissingNode() || data.isNull()) {
                return Optional.empty();
            }
            return Optional.of(new FlagDecision(
                    data.path("key").asText(key),
                    data.path("type").isTextual() ? data.path("type").asText() : null,
                    data.path("value").isNull() ? null : data.path("value").asText(),
                    data.path("hitRule").asText(),
                    data.path("reason").asText(),
                    data.path("bucket").isNull() ? null : data.path("bucket").asInt(),
                    data.path("flagVersion").isNull() ? null : data.path("flagVersion").asInt(),
                    data.path("configVersion").asLong(),
                    FlagDecision.SOURCE_SERVER));
        } catch (Exception e) {
            log.warn("服务端不可达（{}ms 超时或连接失败），走本地兜底：{}", timeout.toMillis(), e.getMessage());
            return Optional.empty();
        }
    }

    private static String trimTrailing(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public static final class Builder {

        private String endpoint = "http://localhost:8080";
        private String sdkKey;
        private String appName = "unknown-app";
        private String publicIpEchoUrl;
        private Duration timeout = Duration.ofMillis(300);
        private Path fallbackFile;
        private boolean subscribe = true;

        private Builder() {
        }

        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public Builder sdkKey(String sdkKey) {
            this.sdkKey = sdkKey;
            return this;
        }

        public Builder appName(String appName) {
            this.appName = appName;
            return this;
        }

        /** 配了才去探测公网 IP；探测失败不影响任何判定 */
        public Builder publicIpEchoUrl(String url) {
            this.publicIpEchoUrl = url;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /** 兜底快照落盘位置，建议 ~/.liveconfig/{app}-{env}.json */
        public Builder fallbackFile(Path file) {
            this.fallbackFile = file;
            return this;
        }

        public Builder subscribe(boolean subscribe) {
            this.subscribe = subscribe;
            return this;
        }

        public LiveconfigClient build() {
            if (sdkKey == null || sdkKey.isBlank()) {
                throw new IllegalArgumentException("sdkKey 必填");
            }
            return new LiveconfigClient(this);
        }
    }
}
