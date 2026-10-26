package com.z.liveconfig.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class EvaluateDTO {

    @NotBlank(message = "flagKey 必填")
    private String flagKey;

    /** 仅控制台「分流调试」使用；SDK 侧由 Key 决定环境，不需要这两个字段 */
    private String appKey;
    private String envKey;

    private String userId;

    private InstanceDto instance;

    private Map<String, String> attributes;

    @Data
    public static class InstanceDto {
        private String privateIp;
        private String publicIp;
        private String appName;
    }
}
