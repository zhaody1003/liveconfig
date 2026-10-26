package com.z.liveconfig.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateAppDTO {

    @NotBlank(message = "应用标识必填")
    private String appKey;

    @NotBlank(message = "应用名称必填")
    private String name;

    private String owner;
    private String color;

    /** 不传则用默认三环境；环境是可创建资源，名字与风险等级都由调用方决定 */
    private java.util.List<CreateEnvDTO> envs;
}
