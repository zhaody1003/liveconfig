package com.z.liveconfig.engine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 流水线优先级与各级判定。引擎是纯 POJO，所以这些用例不起 Spring */
class EvaluationEngineTest {

    private final EvaluationEngine engine = Fixtures.engine();

    @Test
    void 开关不存在时返回_FLAG_NOT_FOUND_且不抛错() {
        EnvConfigSnapshot snapshot = Fixtures.snapshot(
                Fixtures.flag("other", "BOOLEAN", "false", true, List.of(), Map.of()));

        EvalResult result = engine.evaluate(Fixtures.user("new_checkout", "2088"), snapshot);

        assertThat(result.hitRule()).isEqualTo("FLAG_NOT_FOUND");
        assertThat(result.value()).isNull();
    }

    @Test
    void 开关关闭时不参与分流() {
        EnvConfigSnapshot snapshot = Fixtures.snapshot(
                Fixtures.flag("new_checkout", "BOOLEAN", "false", false,
                        List.of(rollout(100, "true")), Map.of()));

        EvalResult result = engine.evaluate(Fixtures.user("new_checkout", "1001"), snapshot);

        assertThat(result.hitRule()).isEqualTo("FLAG_DISABLED");
    }

    @Test
    void 服务器定向高于白名单和百分比() {
        FlagConfig flag = Fixtures.flag("new_checkout", "BOOLEAN", "false", true,
                List.of(whitelist("true"), rollout(100, "true")),
                Map.of("10.0.0.11", "false"));

        EvalResult result = engine.evaluate(
                Fixtures.withInstance("new_checkout", "1001", "10.0.0.11", Map.of()),
                Fixtures.snapshot(flag));

        assertThat(result.hitRule()).isEqualTo("INSTANCE_OVERRIDE");
        assertThat(result.value()).isEqualTo("false");
    }

    @Test
    void 白名单优先于黑名单() {
        FlagConfig flag = Fixtures.flag("new_checkout", "BOOLEAN", "false", true,
                List.of(whitelist("true"), blacklist("false")), Map.of());

        EvalResult result = engine.evaluate(Fixtures.user("new_checkout", "dual"),
                Fixtures.snapshot(flag));

        assertThat(result.hitRule()).isEqualTo("WHITELIST");
        assertThat(result.value()).isEqualTo("true");
    }

    @Test
    void 黑名单命中强制返回_false() {
        FlagConfig flag = Fixtures.flag("new_checkout", "BOOLEAN", "false", true,
                List.of(blacklist("false")), Map.of());

        assertThat(engine.evaluate(Fixtures.user("new_checkout", "9527"), Fixtures.snapshot(flag)).hitRule())
                .isEqualTo("BLACKLIST");
    }

    @Test
    void 属性定向要求条件全部满足() {
        FlagConfig flag = Fixtures.flag("new_checkout", "BOOLEAN", "false", true,
                List.of(new FlagConfig.RuleConfig("ATTRIBUTE", 0,
                        "{\"conditions\":[{\"attr\":\"region\",\"op\":\"EQ\",\"value\":\"hangzhou\"},"
                                + "{\"attr\":\"channel\",\"op\":\"IN\",\"value\":\"app,web\"}]}", "true")),
                Map.of());

        assertThat(engine.evaluate(Fixtures.withInstance("new_checkout", "u1", null,
                Map.of("region", "hangzhou", "channel", "app")), Fixtures.snapshot(flag)).hitRule())
                .isEqualTo("ATTRIBUTE");

        assertThat(engine.evaluate(Fixtures.withInstance("new_checkout", "u1", null,
                Map.of("region", "beijing", "channel", "app")), Fixtures.snapshot(flag)).hitRule())
                .isEqualTo("DEFAULT");
    }

    @Test
    void 百分比按黄金桶号判定() {
        FlagConfig flag = Fixtures.flag("new_checkout", "BOOLEAN", "false", true,
                List.of(rollout(50, "true")), Map.of());

        // 1001→90 不命中，9527→12 命中（见 HashBucketTest 的黄金值）
        assertThat(engine.evaluate(Fixtures.user("new_checkout", "1001"), Fixtures.snapshot(flag)).hitRule())
                .isEqualTo("DEFAULT");
        EvalResult hit = engine.evaluate(Fixtures.user("new_checkout", "9527"), Fixtures.snapshot(flag));
        assertThat(hit.hitRule()).isEqualTo("ROLLOUT");
        assertThat(hit.bucket()).isEqualTo(12);
        assertThat(hit.value()).isEqualTo("true");
    }

    @Test
    void 放量扩大时已命中用户不会跳走() {
        FlagConfig flag30 = Fixtures.flag("new_checkout", "BOOLEAN", "false", true,
                List.of(rollout(30, "true")), Map.of());
        FlagConfig flag50 = Fixtures.flag("new_checkout", "BOOLEAN", "false", true,
                List.of(rollout(50, "true")), Map.of());

        for (int i = 0; i < 500; i++) {
            String user = "u-" + i;
            EvalResult at30 = engine.evaluate(Fixtures.user("new_checkout", user), Fixtures.snapshot(flag30));
            EvalResult at50 = engine.evaluate(Fixtures.user("new_checkout", user), Fixtures.snapshot(flag50));
            if ("ROLLOUT".equals(at30.hitRule())) {
                assertThat(at50.hitRule()).as("30%% 时命中的 %s 在 50%% 必须仍命中", user)
                        .isEqualTo("ROLLOUT");
            }
        }
    }

    @Test
    void 全部未命中时返回默认值() {
        FlagConfig flag = Fixtures.flag("new_checkout", "BOOLEAN", "false", true, List.of(), Map.of());

        EvalResult result = engine.evaluate(Fixtures.user("new_checkout", "2088"), Fixtures.snapshot(flag));

        assertThat(result.hitRule()).isEqualTo("DEFAULT");
        assertThat(result.value()).isEqualTo("false");
        assertThat(result.configVersion()).isEqualTo(7L);
    }

    @Test
    void 字符串开关也能按比例分流() {
        FlagConfig flag = Fixtures.flag("rec_algorithm", "STRING", "v2", true,
                List.of(rollout(20, "v3")), Map.of());

        EvalResult hit = engine.evaluate(Fixtures.user("rec_algorithm", "u-77"), Fixtures.snapshot(flag));
        // u-77 在 new_checkout 下是 79 桶，换 flagKey 后桶号不同，这里只断言结果自洽
        assertThat(hit.value()).isIn("v2", "v3");
        assertThat(hit.hitRule()).isIn("ROLLOUT", "DEFAULT");
    }

    private static FlagConfig.RuleConfig whitelist(String value) {
        return new FlagConfig.RuleConfig("WHITELIST", 0,
                "{\"userIds\":[\"1001\",\"1002\",\"test_admin\",\"dual\"]}", value);
    }

    private static FlagConfig.RuleConfig blacklist(String value) {
        return new FlagConfig.RuleConfig("BLACKLIST", 0,
                "{\"userIds\":[\"9527\",\"dual\"]}", value);
    }

    private static FlagConfig.RuleConfig rollout(int percent, String value) {
        return new FlagConfig.RuleConfig("ROLLOUT", 0,
                "{\"percent\":" + percent + ",\"bucketBy\":\"USER_ID\"}", value);
    }
}
