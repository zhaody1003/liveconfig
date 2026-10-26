package com.z.liveconfig.engine;

import java.util.Optional;

/**
 * 一条分流手段 = 一个实现类。加一种手段是加类，不改主流程。
 * 返回空表示「不归我管 / 未命中」，引擎继续往下走。
 */
public interface RuleEvaluator {

    /** 展示名，同时是 EvalResult.hitRule 的取值 */
    String name();

    Optional<Decision> evaluate(EvalRequest request, FlagConfig flag);

    record Decision(String value, String hitRule, String reason, Integer bucket) {

        public static Decision of(String value, String reason) {
            return new Decision(value, null, reason, null);
        }

        public static Decision bucketed(String value, String reason, int bucket) {
            return new Decision(value, null, reason, bucket);
        }
    }
}
