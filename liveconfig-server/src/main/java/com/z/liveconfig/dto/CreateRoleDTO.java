package com.z.liveconfig.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class CreateRoleDTO {

    @NotBlank(message = "appKey 必填")
    private String appKey;

    @NotBlank(message = "角色名称必填")
    private String name;

    @NotBlank(message = "roleKey 必填")
    private String roleKey;

    @jakarta.validation.constraints.NotEmpty(message = "至少选一个权限点")
    private List<String> perms;
}
