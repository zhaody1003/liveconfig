package com.z.liveconfig.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.z.liveconfig.engine.rules.AttributeRule;
import com.z.liveconfig.engine.rules.BlacklistRule;
import com.z.liveconfig.engine.rules.InstanceOverrideRule;
import com.z.liveconfig.engine.rules.RolloutRule;
import com.z.liveconfig.engine.rules.WhitelistRule;

import java.util.List;
import java.util.Map;

/** 测试与 SDK 快照组装共用的构造助手 */
public final class Fixtures {

    private Fixtures() {
    }

    public static EvaluationEngine engine() {
        ObjectMapper mapper = new ObjectMapper();
        return new EvaluationEngine(List.of(
                new InstanceOverrideRule(),
                new WhitelistRule(mapper),
                new BlacklistRule(mapper),
                new AttributeRule(mapper),
                new RolloutRule(mapper)));
    }

    public static FlagConfig flag(String key, String type, String def, boolean enabled,
                                  List<FlagConfig.RuleConfig> rules,
                                  Map<String, String> overrides) {
        return new FlagConfig(1L, key, type, def, enabled, 1, rules, overrides);
    }

    public static EnvConfigSnapshot snapshot(FlagConfig... flags) {
        return new EnvConfigSnapshot("order-service", "production", 7L, List.of(flags));
    }

    public static EvalRequest user(String flagKey, String userId) {
        return EvalRequest.ofUser(flagKey, userId);
    }

    public static EvalRequest withInstance(String flagKey, String userId, String privateIp,
                                           Map<String, String> attributes) {
        return new EvalRequest(flagKey, userId,
                new EvalRequest.Instance(privateIp, null, "ff-demo"), attributes);
    }
}
