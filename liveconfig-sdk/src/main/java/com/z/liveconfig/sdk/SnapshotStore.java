package com.z.liveconfig.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 本地兜底快照：内存 + 落盘。
 *
 * 落盘是为了进程重启后仍有兜底 —— 否则「服务端没起来 + 应用先起来」这一刻所有开关都退化成长串默认值。
 */
final class SnapshotStore {

    private static final Logger log = LoggerFactory.getLogger(SnapshotStore.class);

    private final ObjectMapper mapper;
    private final HttpClient http;
    private final String endpoint;
    private final String sdkKey;
    private final Path file;

    private volatile Snapshot current = Snapshot.EMPTY;

    SnapshotStore(ObjectMapper mapper, HttpClient http, String endpoint, String sdkKey, Path file) {
        this.mapper = mapper;
        this.http = http;
        this.endpoint = endpoint;
        this.sdkKey = sdkKey;
        this.file = file;
    }

    Snapshot current() {
        return current;
    }

    /** 启动时先读盘，再尝试拉一次最新 */
    void loadFromDisk() {
        if (file == null || !Files.exists(file)) {
            return;
        }
        try {
            current = mapper.readValue(Files.readString(file, StandardCharsets.UTF_8), Snapshot.class);
            log.info("已加载本地兜底快照 configVersion={} file={}", current.configVersion(), file);
        } catch (Exception e) {
            log.warn("本地兜底快照读取失败，忽略：{}", e.getMessage());
        }
    }

    boolean refresh() {
        try {
            String body = httpGet(endpoint + "/sdk/v1/config");
            JsonNode data = mapper.readTree(body).path("data");
            if (data.isMissingNode() || data.isNull()) {
                return false;
            }
            current = mapper.treeToValue(data, Snapshot.class);
            persist();
            return true;
        } catch (Exception e) {
            log.warn("快照刷新失败，继续使用本地兜底 configVersion={}：{}",
                    current.configVersion(), e.getMessage());
            return false;
        }
    }

    private void persist() {
        if (file == null) {
            return;
        }
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, mapper.writeValueAsString(current), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("兜底快照落盘失败：{}", e.getMessage());
        }
    }

    String httpGet(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("X-SDK-Key", sdkKey)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return response.body();
    }

    Map<String, Object> loggable() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("configVersion", current.configVersion());
        map.put("flags", current.flags() == null ? 0 : current.flags().size());
        return map;
    }
}
