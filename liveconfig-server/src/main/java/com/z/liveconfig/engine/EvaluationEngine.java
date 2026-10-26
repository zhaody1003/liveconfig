package com.z.liveconfig.engine;

import java.util.List;
import java.util.Optional;

/**
 * 分流引擎。不依赖 Spring、不碰数据库、不碰 servlet —— 输入请求 + 配置快照，输出结论。
 * 全系统「算得准」这件事只发生在这里，所以它能被纯单元测试覆盖。
 */
public class EvaluationEngine {

    private final List<RuleEvaluator> pipeline;

    public EvaluationEngine(List<RuleEvaluator> pipeline) {
        this.pipeline = List.copyOf(pipeline);
    }

    public EvalResult evaluate(EvalRequest request, EnvConfigSnapshot snapshot) {
        FlagConfig flag = snapshot.find(request.flagKey());
        if (flag == null) {
            return EvalResult.notFound(request.flagKey(), snapshot.configVersion());
        }
        if (!flag.enabled()) {
            return EvalResult.disabled(flag, snapshot.configVersion());
        }
        for (RuleEvaluator rule : pipeline) {
            Optional<RuleEvaluator.Decision> hit = rule.evaluate(request, flag);
            if (hit.isPresent()) {
                return EvalResult.of(flag, hit.get(), snapshot.configVersion());
            }
        }
        return EvalResult.of(flag, RuleEvaluator.Decision.of(flag.defaultValue(),
                "以上规则均未命中，返回默认值"), snapshot.configVersion());
    }
}
