package com.z.liveconfig.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class EvaluateBatchDTO {

    @NotEmpty(message = "flagKeys 不能为空")
    private List<String> flagKeys;

    private String userId;

    @NotNull(message = "instance 必填")
    private EvaluateDTO.InstanceDto instance;

    private Map<String, String> attributes;
}
