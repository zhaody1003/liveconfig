package com.z.liveconfig.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** SSE 订阅线程：收到 config-change 就重拉快照，断线指数退避重连 */
final class SseSubscriber implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(SseSubscriber.class);

    private final ObjectMapper mapper;
    private final HttpClient http;
    private final String endpoint;
    private final String sdkKey;
    private final SnapshotStore store;

    private volatile boolean stopped;
    private volatile long lastVersion = -1;

    SseSubscriber(ObjectMapper mapper, HttpClient http, String endpoint, String sdkKey, SnapshotStore store) {
        this.mapper = mapper;
        this.http = http;
        this.endpoint = endpoint;
        this.sdkKey = sdkKey;
        this.store = store;
    }

    void stop() {
        stopped = true;
    }

    @Override
    public void run() {
        long backoffMs = 1000;
        while (!stopped) {
            try {
                connect();
                backoffMs = 1000;
            } catch (Exception e) {
                if (!stopped) {
                    log.warn("SSE 连接中断，{}ms 后重连：{}", backoffMs, e.getMessage());
                    sleep(backoffMs);
                    backoffMs = Math.min(backoffMs * 2, 30_000);
                }
            }
        }
    }

    private void connect() throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint + "/sdk/v1/stream"))
                .header("X-SDK-Key", sdkKey)
                .header("Accept", "text/event-stream")
                .GET();
        if (lastVersion >= 0) {
            builder.header("Last-Event-ID", String.valueOf(lastVersion));
        }
        HttpResponse<java.util.stream.Stream<String>> response = http.send(builder.build(),
                HttpResponse.BodyHandlers.ofLines());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("SSE HTTP " + response.statusCode());
        }
        log.info("SSE 已连接 {}", endpoint + "/sdk/v1/stream");
        final String[] currentEvent = {null};
        response.body().forEach(line -> {
            if (line.startsWith("event:")) {
                currentEvent[0] = line.substring(6).trim();
            } else if (line.startsWith("data:")) {
                handle(currentEvent[0], line.substring(5).trim());
            } else if (line.isEmpty()) {
                currentEvent[0] = null;
            }
        });
        if (!stopped) {
            throw new IllegalStateException("SSE 流被服务端关闭");
        }
    }

    private void handle(String event, String data) {
        try {
            JsonNode node = mapper.readTree(data);
            long version = node.path("configVersion").asLong(lastVersion);
            lastVersion = version;
            if ("config-change".equals(event)) {
                log.info("收到 config-change version={} changedKeys={}，重拉快照",
                        version, node.path("changedKeys"));
                store.refresh();
            } else if ("hello".equals(event)) {
                long serverVersion = version;
                if (lastVersion >= 0 && serverVersion != lastVersion) {
                    store.refresh();
                }
            }
        } catch (Exception e) {
            log.debug("SSE 事件解析失败：{}", e.getMessage());
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stopped = true;
        }
    }
}
