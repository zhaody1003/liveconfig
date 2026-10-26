package com.z.liveconfig.vo;

import lombok.Data;

@Data
public class MonitorOverviewVO {

    private String appKey;
    private String envKey;
    private Long configVersion;

    /** 活跃 SSE 连接数，口径是实例数 */
    private Integer onlineSdk;
}
