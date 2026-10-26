package com.z.liveconfig.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateDefaultDTO {

    @NotBlank(message = "value 必填")
    private String value;
}
