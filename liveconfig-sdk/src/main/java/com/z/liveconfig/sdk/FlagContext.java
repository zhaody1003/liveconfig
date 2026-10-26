package com.z.liveconfig.sdk;

import java.util.Collections;
import java.util.Map;

/**
 * 一次取值的上下文。
 *
 * instance 是调用方进程自己所在机器的身份：内网 IP 决定「服务器定向」是否命中，
 * userId 决定白/黑名单与百分比分桶。
 */
public record FlagContext(String userId, InstanceInfo instance, Map<String, String> attributes) {

    public record InstanceInfo(String privateIp, String publicIp, String appName) {
    }

    public static FlagContext ofUser(String userId) {
        return new FlagContext(userId, null, Map.of());
    }

    public static FlagContext ofUser(String userId, Map<String, String> attributes) {
        return new FlagContext(userId, null, attributes);
    }

    public static FlagContext anonymous() {
        return new FlagContext(null, null, Map.of());
    }

    public Map<String, String> attributesOrEmpty() {
        return attributes == null ? Collections.emptyMap() : attributes;
    }
}
