package com.z.liveconfig.security;

import com.z.liveconfig.common.BizException;
import com.z.liveconfig.entity.App;
import com.z.liveconfig.entity.Env;
import com.z.liveconfig.entity.SdkKey;
import com.z.liveconfig.mapper.AppMapper;
import com.z.liveconfig.mapper.EnvMapper;
import com.z.liveconfig.mapper.SdkKeyMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * SDK 侧认证：只认 X-SDK-Key，明文查不到（库里只有 SHA-256）。
 *
 * Key 的权限边界就是「读某个应用某个环境的配置」，任何写接口都不在它的可达范围内 ——
 * /sdk/** 与 /api/** 走两套完全分开的拦截器。
 */
@Component
@RequiredArgsConstructor
public class SdkKeyInterceptor implements HandlerInterceptor {

    public static final String ATTR_ENV = "liveconfig.sdk.env";
    public static final String ATTR_APP = "liveconfig.sdk.app";

    private final SdkKeyMapper sdkKeyMapper;
    private final EnvMapper envMapper;
    private final AppMapper appMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String key = request.getHeader("X-SDK-Key");
        if (key == null || key.isBlank()) {
            throw BizException.unauthorized("缺少 X-SDK-Key");
        }
        SdkKey sdkKey = sdkKeyMapper.selectOne(new LambdaQueryWrapper<SdkKey>()
                .eq(SdkKey::getKeyHash, cn.hutool.crypto.digest.DigestUtil.sha256Hex(key.trim()))
                .last("LIMIT 1"));
        if (sdkKey == null) {
            throw BizException.unauthorized("SDK Key 无效或已被重置");
        }
        Env env = envMapper.selectById(sdkKey.getEnvId());
        if (env == null) {
            throw BizException.unauthorized("SDK Key 对应的环境已删除");
        }
        App app = appMapper.selectById(env.getAppId());
        request.setAttribute(ATTR_ENV, env);
        request.setAttribute(ATTR_APP, app);
        return true;
    }
}
