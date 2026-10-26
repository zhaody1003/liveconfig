package com.z.liveconfig.vo;

import lombok.Data;

@Data
public class EnvVO {

    private Long id;

    private String envKey;
    private String color;
    private Boolean highRisk;
    private Long configVersion;
    private Integer flagCount;

    /** 在线 SDK 数 = 该环境当前存活的 SSE 连接数，口径是实例数不是机器数 */
    private Integer onlineSdk;

    private String serverKeyMasked;
    private String clientKeyMasked;
}
