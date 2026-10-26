package com.z.liveconfig.engine.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.z.liveconfig.engine.EvalRequest;
import com.z.liveconfig.engine.FlagConfig;
import com.z.liveconfig.engine.RuleEvaluator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 属性定向：conditions 全部满足（AND）才算命中 */
public class AttributeRule implements RuleEvaluator {

    private final ObjectMapper mapper;

    public AttributeRule(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "ATTRIBUTE";
    }

    @Override
    public Optional<Decision> evaluate(EvalRequest request, FlagConfig flag) {
        for (FlagConfig.RuleConfig rule : flag.rulesOf("ATTRIBUTE")) {
            List<String> failed = new ArrayList<>();
            int total = 0;
            for (JsonNode c : conditions(rule.config())) {
                total++;
                String attr = c.path("attr").asText();
                String op = c.path("op").asText("EQ");
                String expect = c.path("value").asText();
                if (!matches(request.attribute(attr), op, expect)) {
                    failed.add(attr + " " + op + " " + expect + "（实际 " + request.attribute(attr) + "）");
                }
            }
            if (total > 0 && failed.isEmpty()) {
                return Optional.of(new Decision(rule.value(), name(),
                        "属性定向：" + total + " 个条件全部满足", null));
            }
        }
        return Optional.empty();
    }

    private JsonNode conditions(String config) {
        try {
            return mapper.readTree(config).path("conditions");
        } catch (Exception e) {
            return mapper.createArrayNode();
        }
    }

    private boolean matches(String actual, String op, String expect) {
        if (actual == null) {
            return "NE".equals(op);
        }
        return switch (op) {
            case "EQ" -> actual.equals(expect);
            case "NE" -> !actual.equals(expect);
            case "IN" -> List.of(expect.split(",")).contains(actual);
            case "GT" -> compare(actual, expect) > 0;
            case "LT" -> compare(actual, expect) < 0;
            default -> false;
        };
    }

    private int compare(String actual, String expect) {
        try {
            return new BigDecimal(actual).compareTo(new BigDecimal(expect));
        } catch (NumberFormatException e) {
            return actual.compareTo(expect);
        }
    }
}
