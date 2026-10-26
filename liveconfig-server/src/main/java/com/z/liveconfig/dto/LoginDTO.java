package com.z.liveconfig.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginDTO {

    @NotBlank(message = "邮箱必填")
    private String email;

    @NotBlank(message = "密码必填")
    private String password;
}
