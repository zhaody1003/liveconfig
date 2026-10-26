package com.z.liveconfig.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.z.liveconfig.dto.EvaluateDTO;
import com.z.liveconfig.engine.EvalRequest;
import com.z.liveconfig.engine.EvalResult;
import com.z.liveconfig.engine.EvaluationEngine;
import com.z.liveconfig.entity.SdkInstance;
import com.z.liveconfig.mapper.SdkInstanceMapper;
import com.z.liveconfig.service.ISdkConfigService;
import com.z.liveconfig.service.ISdkEvaluateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SdkEvaluateServiceImpl implements ISdkEvaluateService {

    private final EvaluationEngine engine;
    private final ISdkConfigService configService;
    private final SdkInstanceMapper instanceMapper;

    @Override
    public EvalResult evaluate(Long envId, EvaluateDTO dto) {
        EvalRequest.Instance instance = registerInstance(envId, dto);
        EvalRequest request = new EvalRequest(dto.getFlagKey(), dto.getUserId(),
                instance, dto.getAttributes() == null ? Map.of() : dto.getAttributes());
        return engine.evaluate(request, configService.snapshot(envId));
    }

    @Override
    public EvalResult evaluateConsole(Long envId, EvaluateDTO dto) {
        EvaluateDTO.InstanceDto ins = dto.getInstance();
        EvalRequest request = new EvalRequest(dto.getFlagKey(), dto.getUserId(),
                ins == null || ins.getPrivateIp() == null || ins.getPrivateIp().isBlank() ? null
                        : new EvalRequest.Instance(ins.getPrivateIp(), ins.getPublicIp(), ins.getAppName()),
                dto.getAttributes() == null ? Map.of() : dto.getAttributes());
        return engine.evaluate(request, configService.snapshot(envId));
    }

    @Override
    public List<EvalResult> evaluateBatch(Long envId, List<String> flagKeys, EvaluateDTO dto) {
        EvalRequest.Instance instance = registerInstance(envId, dto);
        var snapshot = configService.snapshot(envId);
        return flagKeys.stream()
                .map(key -> engine.evaluate(new EvalRequest(key, dto.getUserId(),
                        instance, dto.getAttributes() == null ? Map.of() : dto.getAttributes()),
                        snapshot))
                .toList();
    }

    /**
     * 顺带登记实例：按 IP 设值时的候选列表来自这张表。
     * 只登记带内网 IP 的调用（Client SDK 不带实例身份）。
     */
    private EvalRequest.Instance registerInstance(Long envId, EvaluateDTO dto) {
        EvaluateDTO.InstanceDto instance = dto.getInstance();
        if (instance == null || instance.getPrivateIp() == null || instance.getPrivateIp().isBlank()) {
            return null;
        }
        SdkInstance existing = instanceMapper.selectOne(new LambdaQueryWrapper<SdkInstance>()
                .eq(SdkInstance::getEnvId, envId)
                .eq(SdkInstance::getPrivateIp, instance.getPrivateIp())
                .last("LIMIT 1"));
        if (existing == null) {
            SdkInstance created = new SdkInstance();
            created.setEnvId(envId);
            created.setPrivateIp(instance.getPrivateIp());
            created.setPublicIp(instance.getPublicIp());
            created.setAppName(instance.getAppName());
            instanceMapper.insert(created);
        } else {
            if (instance.getPublicIp() != null) {
                existing.setPublicIp(instance.getPublicIp());
            }
            if (instance.getAppName() != null) {
                existing.setAppName(instance.getAppName());
            }
            instanceMapper.updateById(existing);
        }
        return new EvalRequest.Instance(
                instance.getPrivateIp(), instance.getPublicIp(), instance.getAppName());
    }
}
