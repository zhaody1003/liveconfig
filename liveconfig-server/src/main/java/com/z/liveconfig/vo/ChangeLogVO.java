package com.z.liveconfig.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChangeLogVO {

    private Long id;
    private String flagKey;
    private String actor;
    private String action;
    private Long configVersion;
    private LocalDateTime createdAt;
    private String beforeJson;
    private String afterJson;
}
