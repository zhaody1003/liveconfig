package com.z.liveconfig.vo;

import lombok.Data;

@Data
public class RuleVO {

    private Long id;

    /** WHITELIST / BLACKLIST / ATTRIBUTE / ROLLOUT */
    private String kind;

    private Integer priority;

    /** 原始 JSON 文本，前端按 kind 解析 */
    private String config;

    private String value;
}
