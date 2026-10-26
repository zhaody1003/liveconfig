package com.z.liveconfig.sdk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * 服务端 /sdk/v1/config 的快照结构，也是本地兜底缓存的落盘格式。
 *
 * ignoreUnknown 是给前向兼容留的：服务端以后加字段，老 SDK 不至于解析失败。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Snapshot(long configVersion, String appKey, String envKey, List<SnapFlag> flags) {

    public static final Snapshot EMPTY = new Snapshot(0L, null, null, List.of());

    public SnapFlag find(String flagKey) {
        if (flags == null) {
            return null;
        }
        return flags.stream().filter(f -> flagKey.equals(f.flagKey())).findFirst().orElse(null);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SnapFlag(String flagKey,
                           String valueType,
                           String defaultValue,
                           boolean enabled,
                           int version,
                           List<SnapRule> rules,
                           Map<String, String> overrides) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SnapRule(String kind, int priority, String config, String value) {
    }
}
