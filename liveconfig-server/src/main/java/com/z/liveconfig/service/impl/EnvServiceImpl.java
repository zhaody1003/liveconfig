package com.z.liveconfig.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.z.liveconfig.common.BizException;
import com.z.liveconfig.dto.CreateEnvDTO;
import com.z.liveconfig.entity.App;
import com.z.liveconfig.entity.Env;
import com.z.liveconfig.entity.FeatureFlag;
import com.z.liveconfig.entity.SdkKey;
import com.z.liveconfig.mapper.EnvMapper;
import com.z.liveconfig.mapper.FeatureFlagMapper;
import com.z.liveconfig.mapper.SdkKeyMapper;
import com.z.liveconfig.security.AccessGuard;
import com.z.liveconfig.security.Perm;
import com.z.liveconfig.service.IEnvService;
import com.z.liveconfig.vo.CreatedEnvVO;
import com.z.liveconfig.vo.EnvVO;
import com.z.liveconfig.push.SseHub;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EnvServiceImpl implements IEnvService {

    private static final List<String> SCOPES = List.of("SERVER", "CLIENT");

    private final EnvMapper envMapper;
    private final SdkKeyMapper sdkKeyMapper;
    private final FeatureFlagMapper flagMapper;
    private final AccessGuard guard;
    private final SseHub sseHub;

    @Override
    public List<EnvVO> list(String appKey) {
        App app = guard.requireApp(appKey);
        guard.require(app.getId(), null, Perm.FLAG_READ);
        return envMapper.selectList(new LambdaQueryWrapper<Env>()
                .eq(Env::getAppId, app.getId())
                .orderByAsc(Env::getId)).stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    @Transactional
    public CreatedEnvVO create(String appKey, CreateEnvDTO dto) {
        App app = guard.requireApp(appKey);
        guard.require(app.getId(), null, Perm.ENV_MANAGE);
        return doCreate(app, dto, true);
    }

    @Override
    @Transactional
    public void createIn(App app, CreateEnvDTO dto) {
        doCreate(app, dto, false);
    }

    @Override
    @Transactional
    public void delete(String appKey, String envKey) {
        App app = guard.requireApp(appKey);
        Env env = guard.requireEnv(app.getId(), envKey);
        guard.require(app.getId(), env.getId(), Perm.ENV_MANAGE);

        long flags = flagMapper.selectCount(new LambdaQueryWrapper<FeatureFlag>()
                .eq(FeatureFlag::getEnvId, env.getId()));
        if (flags > 0) {
            throw BizException.conflict("该环境下还有 " + flags + " 个开关，先删除开关或改环境名");
        }
        sdkKeyMapper.delete(new LambdaQueryWrapper<SdkKey>().eq(SdkKey::getEnvId, env.getId()));
        envMapper.deleteById(env.getId());
    }

    @Override
    @Transactional
    public CreatedEnvVO resetKeys(String appKey, String envKey) {
        App app = guard.requireApp(appKey);
        Env env = guard.requireEnv(app.getId(), envKey);
        guard.require(app.getId(), env.getId(), Perm.ENV_MANAGE);

        sdkKeyMapper.delete(new LambdaQueryWrapper<SdkKey>().eq(SdkKey::getEnvId, env.getId()));
        CreatedEnvVO vo = new CreatedEnvVO();
        for (String scope : SCOPES) {
            String plain = insertKey(env.getId(), scope);
            if ("SERVER".equals(scope)) {
                vo.setServerKeyPlain(plain);
            } else {
                vo.setClientKeyPlain(plain);
            }
        }
        // Key 先入库再组装 VO，否则响应里的 keyMasked 会是空的
        vo.setEnv(toVO(env));
        return vo;
    }

    private CreatedEnvVO doCreate(App app, CreateEnvDTO dto, boolean returnKeys) {
        if (envMapper.selectCount(new LambdaQueryWrapper<Env>()
                .eq(Env::getAppId, app.getId()).eq(Env::getEnvKey, dto.getEnvKey())) > 0) {
            throw BizException.conflict("环境已存在：" + dto.getEnvKey());
        }
        Env env = new Env();
        env.setAppId(app.getId());
        env.setEnvKey(dto.getEnvKey());
        env.setColor(dto.getColor() == null ? "#6366f1" : dto.getColor());
        env.setHighRisk(Boolean.TRUE.equals(dto.getHighRisk()));
        env.setConfigVersion(0L);
        envMapper.insert(env);

        CreatedEnvVO vo = new CreatedEnvVO();
        for (String scope : SCOPES) {
            String plain = insertKey(env.getId(), scope);
            if ("SERVER".equals(scope)) {
                vo.setServerKeyPlain(plain);
            } else {
                vo.setClientKeyPlain(plain);
            }
        }
        vo.setEnv(toVO(env));
        return returnKeys ? vo : null;
    }

    private String insertKey(Long envId, String scope) {
        String plain = "lc-" + scope.toLowerCase() + "-" + IdUtil.fastSimpleUUID().substring(0, 20);
        SdkKey key = new SdkKey();
        key.setEnvId(envId);
        key.setScope(scope);
        key.setKeyHash(DigestUtil.sha256Hex(plain));
        key.setKeyMasked(mask(plain));
        sdkKeyMapper.insert(key);
        return plain;
    }

    private String mask(String plain) {
        return plain.substring(0, 8) + "…" + plain.substring(plain.length() - 4);
    }

    private EnvVO toVO(Env env) {
        EnvVO vo = new EnvVO();
        vo.setId(env.getId());
        vo.setEnvKey(env.getEnvKey());
        vo.setColor(env.getColor());
        vo.setHighRisk(env.getHighRisk());
        vo.setConfigVersion(env.getConfigVersion());
        vo.setFlagCount(Math.toIntExact(flagMapper.selectCount(
                new LambdaQueryWrapper<FeatureFlag>().eq(FeatureFlag::getEnvId, env.getId()))));
        vo.setOnlineSdk(sseHub.count(env.getId()));
        sdkKeyMapper.selectList(new LambdaQueryWrapper<SdkKey>().eq(SdkKey::getEnvId, env.getId()))
                .forEach(k -> {
                    if ("SERVER".equals(k.getScope())) {
                        vo.setServerKeyMasked(k.getKeyMasked());
                    } else {
                        vo.setClientKeyMasked(k.getKeyMasked());
                    }
                });
        return vo;
    }
}
