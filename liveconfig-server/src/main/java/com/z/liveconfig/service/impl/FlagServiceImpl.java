package com.z.liveconfig.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.z.liveconfig.common.BizException;
import com.z.liveconfig.common.ValueTypes;
import com.z.liveconfig.dto.CreateFlagDTO;
import com.z.liveconfig.dto.UpdateDefaultDTO;
import com.z.liveconfig.entity.App;
import com.z.liveconfig.entity.Env;
import com.z.liveconfig.entity.FeatureFlag;
import com.z.liveconfig.entity.FlagInstanceOverride;
import com.z.liveconfig.entity.FlagRule;
import com.z.liveconfig.mapper.FeatureFlagMapper;
import com.z.liveconfig.mapper.FlagInstanceOverrideMapper;
import com.z.liveconfig.mapper.FlagRuleMapper;
import com.z.liveconfig.push.ConfigChangePublisher;
import com.z.liveconfig.security.AccessGuard;
import com.z.liveconfig.security.Perm;
import com.z.liveconfig.service.ConfigVersionService;
import com.z.liveconfig.service.IFlagService;
import com.z.liveconfig.service.IHistoryService;
import com.z.liveconfig.vo.FlagDetailVO;
import com.z.liveconfig.vo.FlagRowVO;
import com.z.liveconfig.vo.RuleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FlagServiceImpl implements IFlagService {

    private final FeatureFlagMapper flagMapper;
    private final FlagRuleMapper ruleMapper;
    private final FlagInstanceOverrideMapper overrideMapper;
    private final AccessGuard guard;
    private final ValueTypes valueTypes;
    private final ObjectMapper mapper;
    private final IHistoryService history;
    private final ConfigVersionService versionService;
    private final ConfigChangePublisher publisher;

    @Override
    public List<FlagRowVO> list(String appKey, String envKey, String keyword) {
        App app = guard.requireApp(appKey);
        Env env = guard.requireEnv(app.getId(), envKey);
        guard.require(app.getId(), env.getId(), Perm.FLAG_READ);

        LambdaQueryWrapper<FeatureFlag> flagQuery = new LambdaQueryWrapper<FeatureFlag>()
                .eq(FeatureFlag::getEnvId, env.getId())
                .orderByAsc(FeatureFlag::getFlagKey);
        if (keyword != null && !keyword.isBlank()) {
            flagQuery.and(w -> w.like(FeatureFlag::getFlagKey, keyword)
                    .or().like(FeatureFlag::getName, keyword));
        }
        List<FeatureFlag> flags = flagMapper.selectList(flagQuery);
        if (flags.isEmpty()) {
            return List.of();
        }

        List<Long> flagIds = flags.stream().map(FeatureFlag::getId).toList();

        Map<Long, Integer> rolloutByFlag = new HashMap<>();
        ruleMapper.selectList(new LambdaQueryWrapper<FlagRule>()
                        .in(FlagRule::getFlagId, flagIds)
                        .eq(FlagRule::getKind, "ROLLOUT")
                        .orderByAsc(FlagRule::getPriority))
                .forEach(r -> rolloutByFlag.putIfAbsent(r.getFlagId(), parsePercent(r.getConfig())));

        Map<Long, Long> overrideCountByFlag = overrideMapper.selectList(
                        new LambdaQueryWrapper<FlagInstanceOverride>().in(FlagInstanceOverride::getFlagId, flagIds))
                .stream().collect(Collectors.groupingBy(FlagInstanceOverride::getFlagId, Collectors.counting()));

        return flags.stream().map(f -> {
            FlagRowVO vo = new FlagRowVO();
            vo.setId(f.getId());
            vo.setEnvId(f.getEnvId());
            vo.setFlagKey(f.getFlagKey());
            vo.setName(f.getName());
            vo.setValueType(f.getValueType());
            vo.setDefaultValue(f.getDefaultValue());
            vo.setEnabled(f.getEnabled());
            vo.setVersion(f.getVersion());
            vo.setRolloutPercent(rolloutByFlag.get(f.getId()));
            vo.setOverrideCount(Math.toIntExact(overrideCountByFlag.getOrDefault(f.getId(), 0L)));
            return vo;
        }).toList();
    }

    @Override
    public FlagDetailVO detail(Long flagId) {
        FeatureFlag flag = guard.requireFlag(flagId);
        Env env = guard.requireFlagEnv(flag);
        App app = guard.requireAppById(env.getAppId());
        guard.require(app.getId(), env.getId(), Perm.FLAG_READ);
        return toDetail(flag, env, app);
    }

    @Override
    @Transactional
    public FlagDetailVO create(CreateFlagDTO dto) {
        App app = guard.requireApp(dto.getAppKey());
        Env env = guard.requireEnv(app.getId(), dto.getEnvKey());
        guard.require(app.getId(), env.getId(), Perm.FLAG_WRITE);
        valueTypes.validateType(dto.getValueType());
        valueTypes.validate(dto.getValueType(), dto.getDefaultValue());

        if (flagMapper.selectCount(new LambdaQueryWrapper<FeatureFlag>()
                .eq(FeatureFlag::getEnvId, env.getId())
                .eq(FeatureFlag::getFlagKey, dto.getFlagKey())) > 0) {
            throw BizException.conflict("该环境下开关已存在：" + dto.getFlagKey());
        }

        FeatureFlag flag = new FeatureFlag();
        flag.setEnvId(env.getId());
        flag.setFlagKey(dto.getFlagKey());
        flag.setName(dto.getName());
        flag.setValueType(dto.getValueType());
        flag.setDefaultValue(dto.getDefaultValue());
        flag.setEnabled(Boolean.TRUE.equals(dto.getEnabled()));
        flag.setVersion(1);
        flagMapper.insert(flag);

        Env bumped = versionService.bump(env);
        history.record(app, flag, guard.currentUser(), "CREATE", null,
                snapshot(flag), bumped.getConfigVersion());
        publisher.published(bumped, app.getAppKey(), List.of(flag.getFlagKey()));
        return toDetail(flag, bumped, app);
    }

    @Override
    @Transactional
    public FlagDetailVO updateDefault(Long flagId, UpdateDefaultDTO dto) {
        FeatureFlag flag = guard.requireFlag(flagId);
        Env env = guard.requireFlagEnv(flag);
        App app = guard.requireAppById(env.getAppId());
        guard.requireWriteValue(env);
        valueTypes.validate(flag.getValueType(), dto.getValue());

        Map<String, Object> before = snapshot(flag);
        String oldValue = flag.getDefaultValue();
        flag.setDefaultValue(dto.getValue());
        flag.setVersion(flag.getVersion() + 1);
        flagMapper.updateById(flag);

        Env bumped = versionService.bump(env);
        history.record(app, flag, guard.currentUser(), "UPDATE_DEFAULT",
                Map.of("defaultValue", oldValue), Map.of("defaultValue", dto.getValue()),
                bumped.getConfigVersion());
        publisher.published(bumped, app.getAppKey(), List.of(flag.getFlagKey()));
        return toDetail(flag, bumped, app);
    }

    @Override
    @Transactional
    public FlagDetailVO toggle(Long flagId, boolean enabled) {
        FeatureFlag flag = guard.requireFlag(flagId);
        Env env = guard.requireFlagEnv(flag);
        App app = guard.requireAppById(env.getAppId());
        guard.requireWriteValue(env);

        Map<String, Object> before = snapshot(flag);
        flag.setEnabled(enabled);
        flag.setVersion(flag.getVersion() + 1);
        flagMapper.updateById(flag);

        Env bumped = versionService.bump(env);
        history.record(app, flag, guard.currentUser(), "UPDATE_DEFAULT", before, snapshot(flag),
                bumped.getConfigVersion());
        publisher.published(bumped, app.getAppKey(), List.of(flag.getFlagKey()));
        return toDetail(flag, bumped, app);
    }

    private Map<String, Object> snapshot(FeatureFlag flag) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("defaultValue", flag.getDefaultValue());
        map.put("enabled", flag.getEnabled());
        map.put("valueType", flag.getValueType());
        return map;
    }

    private FlagDetailVO toDetail(FeatureFlag flag, Env env, App app) {
        FlagDetailVO vo = new FlagDetailVO();
        vo.setId(flag.getId());
        vo.setAppKey(app.getAppKey());
        vo.setEnvKey(env.getEnvKey());
        vo.setFlagKey(flag.getFlagKey());
        vo.setName(flag.getName());
        vo.setValueType(flag.getValueType());
        vo.setDefaultValue(flag.getDefaultValue());
        vo.setEnabled(flag.getEnabled());
        vo.setVersion(flag.getVersion());
        vo.setUpdatedAt(flag.getUpdatedAt());
        vo.setOverrideCount(Math.toIntExact(overrideMapper.selectCount(
                new LambdaQueryWrapper<FlagInstanceOverride>().eq(FlagInstanceOverride::getFlagId, flag.getId()))));

        List<RuleVO> rules = ruleMapper.selectList(new LambdaQueryWrapper<FlagRule>()
                        .eq(FlagRule::getFlagId, flag.getId())
                        .orderByAsc(FlagRule::getPriority))
                .stream().map(r -> {
                    RuleVO rv = new RuleVO();
                    rv.setId(r.getId());
                    rv.setKind(r.getKind());
                    rv.setPriority(r.getPriority());
                    rv.setConfig(r.getConfig());
                    rv.setValue(r.getValue());
                    return rv;
                }).toList();
        vo.setRules(rules);
        rules.stream()
                .filter(r -> "ROLLOUT".equals(r.getKind()))
                .findFirst()
                .ifPresent(r -> vo.setRolloutPercent(parsePercent(r.getConfig())));
        return vo;
    }

    private Integer parsePercent(String config) {
        try {
            return mapper.readTree(config).path("percent").asInt();
        } catch (Exception e) {
            return null;
        }
    }
}
