package com.z.liveconfig.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 本地兜底求值：服务端不可达时用。
 *
 * 规则与优先级和服务端引擎一致（服务器定向 → 白 → 黑 → 属性 → 百分比 → 默认值），
 * 差别只在它读的是本机缓存的快照，所以结果可能落后于刚发生的变更 —— 这是降级的代价，
 * 但绝不会「算错方向」：同一个用户在 30% 放量下命中，断网后仍然命中。
 */
final class FallbackEvaluator {

    private FallbackEvaluator() {
    }

    static Optional<FlagDecision> evaluate(ObjectMapper mapper, Snapshot snapshot,
                                           String flagKey, FlagContext ctx) {
        Snapshot.SnapFlag flag = snapshot.find(flagKey);
        if (flag == null) {
            return Optional.empty();
        }
        if (!flag.enabled()) {
            return Optional.of(new FlagDecision(flagKey, flag.valueType(), null, "FLAG_DISABLED",
                    "开关关闭（本地兜底）", null, flag.version(), snapshot.configVersion(), "FALLBACK"));
        }

        String ip = ctx.instance() == null ? null : ctx.instance().privateIp();
        if (ip != null && flag.overrides() != null && flag.overrides().containsKey(ip)) {
            String value = flag.overrides().get(ip);
            return Optional.of(hit(flag, snapshot, "INSTANCE_OVERRIDE",
                    "服务器定向：" + ip + " 已设值 " + value + "（本地兜底）", null, value));
        }

        if (ctx.userId() != null) {
            Optional<FlagDecision> list = idList(mapper, flag, snapshot, "WHITELIST", ctx.userId(), true);
            if (list.isPresent()) {
                return list;
            }
            list = idList(mapper, flag, snapshot, "BLACKLIST", ctx.userId(), false);
            if (list.isPresent()) {
                return list;
            }
        }

        Optional<FlagDecision> attribute = attribute(mapper, flag, snapshot, ctx);
        if (attribute.isPresent()) {
            return attribute;
        }

        Optional<FlagDecision> rollout = rollout(mapper, flag, snapshot, ctx);
        if (rollout.isPresent()) {
            return rollout;
        }

        return Optional.of(hit(flag, snapshot, "DEFAULT",
                "以上规则均未命中，返回默认值（本地兜底）", null, flag.defaultValue()));
    }

    private static Optional<FlagDecision> idList(ObjectMapper mapper, Snapshot.SnapFlag flag,
                                                 Snapshot snapshot, String kind,
                                                 String userId, boolean whitelist) {
        for (Snapshot.SnapRule rule : rulesOf(flag, kind)) {
            List<String> ids = new ArrayList<>();
            try {
                mapper.readTree(rule.config()).path("userIds").forEach(n -> ids.add(n.asText()));
            } catch (Exception e) {
                continue;
            }
            if (ids.contains(userId)) {
                String label = whitelist ? "白名单" : "黑名单";
                return Optional.of(hit(flag, snapshot, kind,
                        label + "：用户 " + userId + " 在名单内（本地兜底）", null, rule.value()));
            }
        }
        return Optional.empty();
    }

    private static Optional<FlagDecision> attribute(ObjectMapper mapper, Snapshot.SnapFlag flag,
                                                    Snapshot snapshot, FlagContext ctx) {
        for (Snapshot.SnapRule rule : rulesOf(flag, "ATTRIBUTE")) {
            try {
                JsonNode conditions = mapper.readTree(rule.config()).path("conditions");
                int total = 0;
                boolean all = true;
                for (JsonNode c : conditions) {
                    total++;
                    String actual = ctx.attributesOrEmpty().get(c.path("attr").asText());
                    if (!matches(actual, c.path("op").asText("EQ"), c.path("value").asText())) {
                        all = false;
                        break;
                    }
                }
                if (total > 0 && all) {
                    return Optional.of(hit(flag, snapshot, "ATTRIBUTE",
                            "属性定向：" + total + " 个条件全部满足（本地兜底）", null, rule.value()));
                }
            } catch (Exception e) {
                // 快照里的规则解析不了就当未命中，继续往下
            }
        }
        return Optional.empty();
    }

    private static Optional<FlagDecision> rollout(ObjectMapper mapper, Snapshot.SnapFlag flag,
                                                  Snapshot snapshot, FlagContext ctx) {
        for (Snapshot.SnapRule rule : rulesOf(flag, "ROLLOUT")) {
            try {
                JsonNode config = mapper.readTree(rule.config());
                int percent = config.path("percent").asInt(-1);
                if (percent < 0) {
                    continue;
                }
                if (percent >= 100) {
                    return Optional.of(hit(flag, snapshot, "ROLLOUT", "放量 100%，全量命中（本地兜底）",
                            null, rule.value()));
                }
                String bucketBy = config.path("bucketBy").asText("USER_ID");
                String subject = "INSTANCE".equals(bucketBy)
                        ? (ctx.instance() == null ? null : ctx.instance().privateIp())
                        : ctx.userId();
                if (subject == null || subject.isBlank()) {
                    continue;
                }
                int bucket = SdkBucket.of(flag.flagKey(), subject);
                if (bucket < percent) {
                    return Optional.of(hit(flag, snapshot, "ROLLOUT",
                            "百分比：bucket " + bucket + " < " + percent + " 命中（本地兜底）",
                            bucket, rule.value()));
                }
                return Optional.empty();
            } catch (Exception e) {
                continue;
            }
        }
        return Optional.empty();
    }

    private static List<Snapshot.SnapRule> rulesOf(Snapshot.SnapFlag flag, String kind) {
        if (flag.rules() == null) {
            return List.of();
        }
        return flag.rules().stream().filter(r -> kind.equals(r.kind())).toList();
    }

    private static FlagDecision hit(Snapshot.SnapFlag flag, Snapshot snapshot,
                                    String hitRule, String reason, Integer bucket, String value) {
        return new FlagDecision(flag.flagKey(), flag.valueType(), value, hitRule, reason,
                bucket, flag.version(), snapshot.configVersion(), "FALLBACK");
    }

    private static boolean matches(String actual, String op, String expect) {
        if (actual == null) {
            return "NE".equals(op);
        }
        return switch (op) {
            case "EQ" -> actual.equals(expect);
            case "NE" -> !actual.equals(expect);
            case "IN" -> List.of(expect.split(",")).contains(actual);
            default -> false;
        };
    }
}
