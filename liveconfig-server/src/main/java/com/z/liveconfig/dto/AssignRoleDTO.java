package com.z.liveconfig.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AssignRoleDTO {

    @NotBlank(message = "appKey 必填")
    private String appKey;

    @NotBlank(message = "email 必填")
    private String email;

    @NotBlank(message = "envKey 必填")
    private String envKey;

    /** 传空字符串表示取消该环境的授权 */
    private String roleKey;
}
