package com.z.liveconfig.vo;

import lombok.Data;

/** 开关列表行：XML 聚合出来的展示视图 */
@Data
public class FlagRowVO {

    private Long id;
    private Long envId;
    private String flagKey;
    private String name;
    private String valueType;
    private String defaultValue;
    private Boolean enabled;
    private Integer version;

    /** 放量比例，无 ROLLOUT 规则时为 null */
    private Integer rolloutPercent;

    /** 按 IP 单独设值的服务器台数 */
    private Integer overrideCount;
}
