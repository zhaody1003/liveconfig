package com.z.liveconfig.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.z.liveconfig.engine.EnvConfigSnapshot;
import com.z.liveconfig.engine.FlagConfig;
import com.z.liveconfig.entity.App;
import com.z.liveconfig.entity.Env;
import com.z.liveconfig.entity.FeatureFlag;
import com.z.liveconfig.entity.FlagInstanceOverride;
import com.z.liveconfig.entity.FlagRule;
import com.z.liveconfig.mapper.AppMapper;
import com.z.liveconfig.mapper.EnvMapper;
import com.z.liveconfig.mapper.FeatureFlagMapper;
import com.z.liveconfig.mapper.FlagInstanceOverrideMapper;
import com.z.liveconfig.mapper.FlagRuleMapper;
import com.z.liveconfig.service.ISdkConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SdkConfigServiceImpl implements ISdkConfigService {

    private final EnvMapper envMapper;
    private final AppMapper appMapper;
    private final FeatureFlagMapper flagMapper;
    private final FlagRuleMapper ruleMapper;
    private final FlagInstanceOverrideMapper overrideMapper;

    @Override
    public EnvConfigSnapshot snapshot(Long envId) {
        Env env = envMapper.selectById(envId);
        if (env == null) {
            return new EnvConfigSnapshot(null, null, 0L, List.of());
        }
        App app = appMapper.selectById(env.getAppId());

        List<FeatureFlag> flags = flagMapper.selectList(new LambdaQueryWrapper<FeatureFlag>()
                .eq(FeatureFlag::getEnvId, envId)
                .orderByAsc(FeatureFlag::getFlagKey));

        List<FlagConfig> configs = flags.stream().map(f -> new FlagConfig(
                f.getId(),
                f.getFlagKey(),
                f.getValueType(),
                f.getDefaultValue(),
                f.getEnabled(),
                f.getVersion(),
                ruleMapper.selectList(new LambdaQueryWrapper<FlagRule>()
                                .eq(FlagRule::getFlagId, f.getId())
                                .orderByAsc(FlagRule::getPriority))
                        .stream()
                        .map(r -> new FlagConfig.RuleConfig(r.getKind(), r.getPriority(),
                                r.getConfig(), r.getValue()))
                        .toList(),
                overrideMap(f.getId()))).toList();

        return new EnvConfigSnapshot(app == null ? null : app.getAppKey(),
                env.getEnvKey(), env.getConfigVersion(), configs);
    }

    private Map<String, String> overrideMap(Long flagId) {
        Map<String, String> map = new LinkedHashMap<>();
        overrideMapper.selectList(new LambdaQueryWrapper<FlagInstanceOverride>()
                        .eq(FlagInstanceOverride::getFlagId, flagId))
                .forEach(o -> map.put(o.getPrivateIp(), o.getValue()));
        return map;
    }
}
