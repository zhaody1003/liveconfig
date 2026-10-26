package com.z.liveconfig.engine;

import java.util.List;

/** 一个环境的完整配置快照，SDK 兜底缓存也是这个结构 */
public record EnvConfigSnapshot(String appKey,
                                String envKey,
                                long configVersion,
                                List<FlagConfig> flags) {

    public FlagConfig find(String flagKey) {
        if (flags == null) {
            return null;
        }
        return flags.stream().filter(f -> f.flagKey().equals(flagKey)).findFirst().orElse(null);
    }
}
