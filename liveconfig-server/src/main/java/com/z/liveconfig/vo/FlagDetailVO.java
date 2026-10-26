package com.z.liveconfig.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class FlagDetailVO {

    private Long id;
    private String appKey;
    private String envKey;
    private String flagKey;
    private String name;
    private String valueType;
    private String defaultValue;
    private Boolean enabled;
    private Integer version;
    private Integer rolloutPercent;
    private Integer overrideCount;
    private List<RuleVO> rules;
    private LocalDateTime updatedAt;
}
