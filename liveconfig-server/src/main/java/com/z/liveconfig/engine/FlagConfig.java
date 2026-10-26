package com.z.liveconfig.engine;

import java.util.List;
import java.util.Map;

/** 引擎输入用的开关配置快照（从 DB 组装出来的不可变视图） */
public record FlagConfig(Long id,
                         String flagKey,
                         String valueType,
                         String defaultValue,
                         boolean enabled,
                         int version,
                         List<RuleConfig> rules,
                         Map<String, String> instanceOverrides) {

    /** kind = WHITELIST / BLACKLIST / ATTRIBUTE / ROLLOUT，config 是原始 JSON 文本 */
    public record RuleConfig(String kind, int priority, String config, String value) {
    }

    public String overrideFor(String privateIp) {
        return instanceOverrides == null ? null : instanceOverrides.get(privateIp);
    }

    public List<RuleConfig> rulesOf(String kind) {
        if (rules == null) {
            return List.of();
        }
        return rules.stream().filter(r -> kind.equals(r.kind())).toList();
    }
}
