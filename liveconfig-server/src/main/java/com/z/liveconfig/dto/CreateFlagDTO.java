package com.z.liveconfig.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateFlagDTO {

    @NotBlank(message = "appKey 必填")
    private String appKey;

    @NotBlank(message = "envKey 必填")
    private String envKey;

    @NotBlank(message = "开关 Key 必填")
    private String flagKey;

    private String name;

    @NotBlank(message = "值类型必填")
    private String valueType;

    @NotBlank(message = "默认值必填")
    private String defaultValue;

    private Boolean enabled = Boolean.TRUE;
}
