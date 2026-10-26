package com.z.liveconfig.engine;

/** 一次分流的完整结论，reason / bucket 都回给调用方，控制台的「分流调试」页直接拿来展示 */
public record EvalResult(String key,
                         String type,
                         String value,
                         String hitRule,
                         String reason,
                         Integer bucket,
                         Integer flagVersion,
                         long configVersion,
                         String source) {

    public static EvalResult of(FlagConfig flag, RuleEvaluator.Decision d, long configVersion) {
        String hitRule = d.hitRule() == null ? "DEFAULT" : d.hitRule();
        return new EvalResult(flag.flagKey(), flag.valueType(), d.value(), hitRule, d.reason(),
                d.bucket(), flag.version(), configVersion, "SERVER");
    }

    public static EvalResult disabled(FlagConfig flag, long configVersion) {
        return new EvalResult(flag.flagKey(), flag.valueType(), null, "FLAG_DISABLED",
                "开关已关闭，按调用方给的默认值兜底", null, flag.version(), configVersion, "SERVER");
    }

    public static EvalResult notFound(String flagKey, long configVersion) {
        return new EvalResult(flagKey, null, null, "FLAG_NOT_FOUND",
                "该环境下不存在这个开关", null, null, configVersion, "SERVER");
    }
}
