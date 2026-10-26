package com.z.liveconfig.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class InviteMemberDTO {

    @NotBlank(message = "appKey 必填")
    private String appKey;

    /** 已有平台账号按 email 邀请；email 不存在时用 name/password 新建账号 */
    @NotBlank(message = "email 必填")
    private String email;

    private String name;
    private String password;

    @NotBlank(message = "roleKey 必填")
    private String roleKey;

    @NotEmpty(message = "至少选择一个环境")
    private List<String> envKeys;
}
