package com.z.liveconfig.service.impl;

import com.z.liveconfig.entity.Env;
import com.z.liveconfig.mapper.EnvMapper;
import com.z.liveconfig.service.ConfigVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConfigVersionServiceImpl implements ConfigVersionService {

    private final EnvMapper envMapper;

    @Override
    public Env bump(Env env) {
        envMapper.bumpConfigVersion(env.getId());
        return envMapper.selectById(env.getId());
    }
}
