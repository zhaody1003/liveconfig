package com.z.liveconfig.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 兜底求值必须与服务端引擎同结论 —— 断网那一刻不能让灰度方向跳变。
 * 这里手搭一份与服务端 /sdk/v1/config 同形态的快照，不依赖 server 模块。
 */
class FallbackEvaluatorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void 服务器定向优先于白名单和百分比() {
        Snapshot snapshot = snapshot(10L, flag("checkout", "BOOLEAN", "false", true,
                List.of(rule("WHITELIST", "{\"userIds\":[\"1001\"]}", "true"),
                        rule("ROLLOUT", "{\"percent\":100,\"bucketBy\":\"USER_ID\"}", "true")),
                Map.of("10.0.0.11", "false")));

        FlagDecision decision = FallbackEvaluator.evaluate(mapper, snapshot, "checkout",
                new FlagContext("1001", new FlagContext.InstanceInfo("10.0.0.11", null, "demo"), Map.of()))
                .orElseThrow();

        assertThat(decision.hitRule()).isEqualTo("INSTANCE_OVERRIDE");
        assertThat(decision.value()).isEqualTo("false");
        assertThat(decision.source()).isEqualTo("FALLBACK");
    }

    @Test
    void 白名单命中返回true() {
        Snapshot snapshot = snapshot(3L, flag("checkout", "BOOLEAN", "false", true,
                List.of(rule("WHITELIST", "{\"userIds\":[\"1001\"]}", "true")), Map.of()));

        FlagDecision decision = FallbackEvaluator.evaluate(mapper, snapshot, "checkout",
                FlagContext.ofUser("1001")).orElseThrow();

        assertThat(decision.value()).isEqualTo("true");
        assertThat(decision.hitRule()).isEqualTo("WHITELIST");
    }

    @Test
    void 开关关闭时不分流() {
        Snapshot snapshot = snapshot(3L, flag("checkout", "BOOLEAN", "false", false,
                List.of(rule("ROLLOUT", "{\"percent\":100,\"bucketBy\":\"USER_ID\"}", "true")), Map.of()));

        FlagDecision decision = FallbackEvaluator.evaluate(mapper, snapshot, "checkout",
                FlagContext.ofUser("1001")).orElseThrow();

        assertThat(decision.hitRule()).isEqualTo("FLAG_DISABLED");
        assertThat(decision.value()).isNull();
    }

    @Test
    void 快照里没有的开关返回空让调用方给默认值() {
        Snapshot snapshot = snapshot(3L, flag("other", "BOOLEAN", "false", true, List.of(), Map.of()));

        assertThat(FallbackEvaluator.evaluate(mapper, snapshot, "checkout", FlagContext.ofUser("1")))
                .isEmpty();
    }

    @Test
    void 兜底桶号与服务端算法一致() {
        // 与服务端 HashBucketTest 的黄金值同一批输入
        assertThat(SdkBucket.of("new_checkout", "1001")).isEqualTo(90);
        assertThat(SdkBucket.of("new_checkout", "2088")).isEqualTo(39);
        assertThat(SdkBucket.of("new_checkout", "test_admin")).isEqualTo(15);
        assertThat(SdkBucket.of("new_checkout", "9527")).isEqualTo(12);
        assertThat(SdkBucket.of("new_checkout", "u-77")).isEqualTo(79);
    }

    @Test
    void 放量40时命中者断网后仍命中() {
        Snapshot snapshot = snapshot(3L, flag("checkout", "BOOLEAN", "false", true,
                List.of(rule("ROLLOUT", "{\"percent\":40,\"bucketBy\":\"USER_ID\"}", "true")), Map.of()));

        int bucket = SdkBucket.of("checkout", "9527");
        FlagDecision decision = FallbackEvaluator.evaluate(mapper, snapshot, "checkout",
                FlagContext.ofUser("9527")).orElseThrow();

        // 桶号决定命中；未命中时走默认值，所以 bucket 只在命中那条分支里出现
        boolean hit = bucket < 40;
        assertThat(decision.value()).isEqualTo(hit ? "true" : "false");
        assertThat(decision.hitRule()).isEqualTo(hit ? "ROLLOUT" : "DEFAULT");
        if (hit) {
            assertThat(decision.bucket()).isEqualTo(bucket);
        }
        assertThat(decision.source()).isEqualTo("FALLBACK");
    }

    private static Snapshot snapshot(long version, Snapshot.SnapFlag... flags) {
        return new Snapshot(version, "order-service", "gray", List.of(flags));
    }

    private static Snapshot.SnapFlag flag(String key, String type, String def, boolean enabled,
                                          List<Snapshot.SnapRule> rules, Map<String, String> overrides) {
        return new Snapshot.SnapFlag(key, type, def, enabled, 1, rules, overrides);
    }

    private static Snapshot.SnapRule rule(String kind, String config, String value) {
        return new Snapshot.SnapRule(kind, 0, config, value);
    }
}
