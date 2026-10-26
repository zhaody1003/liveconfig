package com.z.liveconfig.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 开关值的唯一校验入口：默认值、实例设值、规则返回值三条写路径都必须走这里。
 * DB 里一律存字符串，类型只决定“什么样的字符串合法”。
 */
@Component
public class ValueTypes {

    private final ObjectMapper mapper;

    public ValueTypes(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void validate(String type, String raw) {
        if (raw == null) {
            throw BizException.badRequest("值不能为空");
        }
        switch (type) {
            case "BOOLEAN" -> {
                if (!"true".equals(raw) && !"false".equals(raw)) {
                    throw BizException.badRequest("BOOLEAN 只接受 true / false，当前值：" + raw);
                }
            }
            case "NUMBER" -> {
                try {
                    new BigDecimal(raw.trim());
                } catch (NumberFormatException e) {
                    throw BizException.badRequest("NUMBER 必须是数字，当前值：" + raw);
                }
            }
            case "JSON" -> {
                JsonNode node;
                try {
                    node = mapper.readTree(raw);
                } catch (Exception e) {
                    throw BizException.badRequest("JSON 不合法：" + e.getMessage());
                }
                // readTree("true") / readTree("123") 都是合法 JSON 标量，
                // 但开关声明成 JSON 类型时，标量会让 SDK 侧 getJson 拿到一个非结构值，语义就糊了
                if (!node.isObject() && !node.isArray()) {
                    throw BizException.badRequest("JSON 类型要求对象或数组，当前值是个标量：" + raw);
                }
            }
            case "STRING" -> {
                // 任意文本
            }
            default -> throw BizException.badRequest("未知的值类型：" + type);
        }
    }

    public void validateType(String type) {
        if (!"BOOLEAN".equals(type) && !"STRING".equals(type) && !"NUMBER".equals(type) && !"JSON".equals(type)) {
            throw BizException.badRequest("不支持的值类型：" + type);
        }
    }
}
