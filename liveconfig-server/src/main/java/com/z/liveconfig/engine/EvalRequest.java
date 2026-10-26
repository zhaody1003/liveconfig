package com.z.liveconfig.engine;

import java.util.Map;

/**
 * 一次分流请求的输入。
 *
 * instance 是发起调用的那台服务器自己上报的身份：内网 IP 作主键，
 * 公网 IP 只用于展示（NAT 后多台机器可能共享同一个公网 IP）。
 */
public record EvalRequest(String flagKey, String userId, Instance instance, Map<String, String> attributes) {

    public record Instance(String privateIp, String publicIp, String appName) {
    }

    public static EvalRequest ofUser(String flagKey, String userId) {
        return new EvalRequest(flagKey, userId, null, Map.of());
    }

    public boolean hasInstance() {
        return instance != null && instance.privateIp() != null && !instance.privateIp().isBlank();
    }

    public String attribute(String name) {
        return attributes == null ? null : attributes.get(name);
    }
}
