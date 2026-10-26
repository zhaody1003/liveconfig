package com.z.liveconfig.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.z.liveconfig.common.BizException;
import com.z.liveconfig.common.ValueTypes;
import com.z.liveconfig.dto.SetInstanceDTO;
import com.z.liveconfig.entity.App;
import com.z.liveconfig.entity.Env;
import com.z.liveconfig.entity.FeatureFlag;
import com.z.liveconfig.entity.FlagInstanceOverride;
import com.z.liveconfig.entity.SdkInstance;
import com.z.liveconfig.mapper.FeatureFlagMapper;
import com.z.liveconfig.mapper.FlagInstanceOverrideMapper;
import com.z.liveconfig.mapper.SdkInstanceMapper;
import com.z.liveconfig.push.ConfigChangePublisher;
import com.z.liveconfig.security.AccessGuard;
import com.z.liveconfig.security.Perm;
import com.z.liveconfig.service.ConfigVersionService;
import com.z.liveconfig.service.IHistoryService;
import com.z.liveconfig.service.IInstanceService;
import com.z.liveconfig.vo.InstanceRowVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InstanceServiceImpl implements IInstanceService {

    private final FlagInstanceOverrideMapper overrideMapper;
    private final SdkInstanceMapper instanceMapper;
    private final FeatureFlagMapper flagMapper;
    private final AccessGuard guard;
    private final ValueTypes valueTypes;
    private final IHistoryService history;
    private final ConfigVersionService versionService;
    private final ConfigChangePublisher publisher;

    @Override
    public List<InstanceRowVO> list(Long flagId) {
        FeatureFlag flag = guard.requireFlag(flagId);
        Env env = guard.requireFlagEnv(flag);
        App app = guard.requireAppById(env.getAppId());
        guard.require(app.getId(), env.getId(), Perm.FLAG_READ);

        Map<String, FlagInstanceOverride> overrides = overrideMapper.selectList(
                        new LambdaQueryWrapper<FlagInstanceOverride>().eq(FlagInstanceOverride::getFlagId, flagId))
                .stream()
                .collect(Collectors.toMap(FlagInstanceOverride::getPrivateIp, Function.identity(), (a, b) -> a));

        // 候选 = 连过的实例 ∪ 已设值的 IP（实例下线了也要能把它设的值清掉）
        Map<String, InstanceRowVO> rows = new LinkedHashMap<>();
        instanceMapper.selectList(new LambdaQueryWrapper<SdkInstance>()
                        .eq(SdkInstance::getEnvId, env.getId())
                        .orderByAsc(SdkInstance::getPrivateIp))
                .forEach(i -> rows.put(i.getPrivateIp(), row(i.getPrivateIp(), i.getPublicIp(), flag)));
        overrides.forEach((ip, ov) -> rows.computeIfAbsent(ip, k -> row(ip, ov.getPublicIp(), flag))
                .setOverrideValue(ov.getValue()));
        return List.copyOf(rows.values());
    }

    @Override
    @Transactional
    public void set(Long flagId, SetInstanceDTO dto) {
        FeatureFlag flag = guard.requireFlag(flagId);
        Env env = guard.requireFlagEnv(flag);
        App app = guard.requireAppById(env.getAppId());
        guard.require(app.getId(), env.getId(), Perm.INSTANCE_WRITE);
        valueTypes.validate(flag.getValueType(), dto.getValue());

        FlagInstanceOverride existing = overrideMapper.selectOne(new LambdaQueryWrapper<FlagInstanceOverride>()
                .eq(FlagInstanceOverride::getFlagId, flagId)
                .eq(FlagInstanceOverride::getPrivateIp, dto.getPrivateIp())
                .last("LIMIT 1"));

        String before = existing == null ? null : existing.getValue();
        if (existing == null) {
            FlagInstanceOverride ov = new FlagInstanceOverride();
            ov.setFlagId(flagId);
            ov.setPrivateIp(dto.getPrivateIp());
            ov.setPublicIp(dto.getPublicIp());
            ov.setValue(dto.getValue());
            overrideMapper.insert(ov);
        } else {
            existing.setValue(dto.getValue());
            if (dto.getPublicIp() != null) {
                existing.setPublicIp(dto.getPublicIp());
            }
            overrideMapper.updateById(existing);
        }

        Env bumped = versionService.bump(env);
        history.record(app, flag, guard.currentUser(), "SET_INSTANCE",
                before == null ? null : Map.of(dto.getPrivateIp(), before),
                Map.of(dto.getPrivateIp(), dto.getValue()), bumped.getConfigVersion());
        publisher.published(bumped, app.getAppKey(), List.of(flag.getFlagKey()));
    }

    @Override
    @Transactional
    public void clear(Long flagId, String privateIp) {
        FeatureFlag flag = guard.requireFlag(flagId);
        Env env = guard.requireFlagEnv(flag);
        App app = guard.requireAppById(env.getAppId());
        guard.require(app.getId(), env.getId(), Perm.INSTANCE_WRITE);

        int deleted = overrideMapper.delete(new LambdaQueryWrapper<FlagInstanceOverride>()
                .eq(FlagInstanceOverride::getFlagId, flagId)
                .eq(FlagInstanceOverride::getPrivateIp, privateIp));
        if (deleted == 0) {
            throw BizException.notFound("该服务器未单独设值：" + privateIp);
        }

        Env bumped = versionService.bump(env);
        history.record(app, flag, guard.currentUser(), "CLEAR_INSTANCE",
                Map.of(privateIp, "已设值"), Map.of(privateIp, "跟随默认"), bumped.getConfigVersion());
        publisher.published(bumped, app.getAppKey(), List.of(flag.getFlagKey()));
    }

    private InstanceRowVO row(String privateIp, String publicIp, FeatureFlag flag) {
        InstanceRowVO vo = new InstanceRowVO();
        vo.setPrivateIp(privateIp);
        vo.setPublicIp(publicIp);
        vo.setValueType(flag.getValueType());
        return vo;
    }
}
