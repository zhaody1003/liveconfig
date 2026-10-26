package com.z.liveconfig.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** 规则整体保存：前端一次提交完整列表，后端全删重插，避免逐条 diff */
@Data
public class SaveRulesDTO {

    @NotEmpty(message = "规则列表不能为空，清空请逐条删除")
    private List<RuleItem> rules;

    @Data
    public static class RuleItem {

        @jakarta.validation.constraints.NotBlank(message = "kind 必填")
        private String kind;

        private Integer priority = 0;

        @jakarta.validation.constraints.NotBlank(message = "config 必填")
        private String config;

        @jakarta.validation.constraints.NotBlank(message = "value 必填")
        private String value;
    }
}
