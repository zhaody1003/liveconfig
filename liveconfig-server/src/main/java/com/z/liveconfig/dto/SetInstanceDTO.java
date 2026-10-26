package com.z.liveconfig.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SetInstanceDTO {

    @NotBlank(message = "内网 IP 必填")
    private String privateIp;

    private String publicIp;

    @NotBlank(message = "value 必填")
    private String value;
}
