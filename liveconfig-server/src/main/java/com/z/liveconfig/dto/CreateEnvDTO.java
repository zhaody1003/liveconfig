package com.z.liveconfig.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateEnvDTO {

    @NotBlank(message = "环境名称必填")
    private String envKey;

    private String color;

    /** true → 该环境写值需要 value:prod 权限 */
    private Boolean highRisk = Boolean.FALSE;
}
