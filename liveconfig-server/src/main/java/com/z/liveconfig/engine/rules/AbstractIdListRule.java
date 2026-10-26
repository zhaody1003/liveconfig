package com.z.liveconfig.engine.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.z.liveconfig.engine.EvalRequest;
import com.z.liveconfig.engine.FlagConfig;
import com.z.liveconfig.engine.RuleEvaluator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 白名单 / 黑名单共用一套读法，只是 kind 与语义不同 */
public abstract class AbstractIdListRule implements RuleEvaluator {

    protected final ObjectMapper mapper;

    protected AbstractIdListRule(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    protected abstract String kind();

    @Override
    public Optional<Decision> evaluate(EvalRequest request, FlagConfig flag) {
        if (request.userId() == null || request.userId().isBlank()) {
            return Optional.empty();
        }
        for (FlagConfig.RuleConfig rule : flag.rulesOf(kind())) {
            List<String> ids = readIds(rule.config());
            if (ids.contains(request.userId())) {
                return Optional.of(new Decision(rule.value(), kind(),
                        name() + "：用户 " + request.userId() + " 在名单内", null));
            }
        }
        return Optional.empty();
    }

    private List<String> readIds(String config) {
        try {
            JsonNode node = mapper.readTree(config).path("userIds");
            List<String> ids = new ArrayList<>();
            node.forEach(n -> ids.add(n.asText()));
            return ids;
        } catch (Exception e) {
            return List.of();
        }
    }
}
