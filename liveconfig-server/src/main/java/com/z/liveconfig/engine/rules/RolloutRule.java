package com.z.liveconfig.engine.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.z.liveconfig.engine.EvalRequest;
import com.z.liveconfig.engine.FlagConfig;
import com.z.liveconfig.engine.HashBucket;
import com.z.liveconfig.engine.RuleEvaluator;

import java.util.Optional;

/**
 * 百分比灰度：bucket = hash(flagKey + 主体) % 100，命中条件 bucket &lt; percent。
 *
 * 用「小于阈值」而不是「落在某个区间」，是为了放量时已命中的用户不会跳走：
 * 30% → 50% 时，原来 bucket &lt; 30 的人仍然 &lt; 50。
 */
public class RolloutRule implements RuleEvaluator {

    private final ObjectMapper mapper;

    public RolloutRule(ObjectMapper mapper) {
        this.mapper = mapper;
    }


    @Override
    public String name() {
        return "ROLLOUT";
    }

    @Override
    public Optional<Decision> evaluate(EvalRequest request, FlagConfig flag) {
        for (FlagConfig.RuleConfig rule : flag.rulesOf("ROLLOUT")) {
            JsonNode config;
            try {
                config = mapper.readTree(rule.config());
            } catch (Exception e) {
                continue;
            }
            int percent = config.path("percent").asInt(-1);
            if (percent < 0) {
                continue;
            }
            if (percent >= 100) {
                return Optional.of(new Decision(rule.value(), name(), "放量 100%，全量命中", null));
            }

            String bucketBy = config.path("bucketBy").asText("USER_ID");
            String subject = "INSTANCE".equals(bucketBy)
                    ? (request.hasInstance() ? request.instance().privateIp() : null)
                    : request.userId();
            if (subject == null || subject.isBlank()) {
                continue;
            }
            int bucket = HashBucket.of(flag.flagKey(), subject);
            if (bucket < percent) {
                return Optional.of(new Decision(rule.value(), name(),
                        "百分比：bucket " + bucket + " < " + percent + " 命中", bucket));
            }
            return Optional.empty();
        }
        return Optional.empty();
    }
}
