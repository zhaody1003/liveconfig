package com.z.liveconfig.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.z.liveconfig.common.BizException;
import com.z.liveconfig.dto.CreateAppDTO;
import com.z.liveconfig.dto.CreateEnvDTO;
import com.z.liveconfig.entity.App;
import com.z.liveconfig.entity.AppRole;
import com.z.liveconfig.entity.AppRolePerm;
import com.z.liveconfig.entity.Env;
import com.z.liveconfig.entity.FeatureFlag;
import com.z.liveconfig.mapper.AppMapper;
import com.z.liveconfig.mapper.AppRoleMapper;
import com.z.liveconfig.mapper.AppRolePermMapper;
import com.z.liveconfig.mapper.EnvMapper;
import com.z.liveconfig.mapper.FeatureFlagMapper;
import com.z.liveconfig.security.AccessGuard;
import com.z.liveconfig.service.IAppService;
import com.z.liveconfig.service.IEnvService;
import com.z.liveconfig.vo.AppVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AppServiceImpl implements IAppService {

    private static final List<String> BUILTIN_ROLES = List.of("viewer", "dev", "release", "admin");

    private final AppMapper appMapper;
    private final EnvMapper envMapper;
    private final FeatureFlagMapper flagMapper;
    private final AppRoleMapper roleMapper;
    private final AppRolePermMapper rolePermMapper;
    private final IEnvService envService;
    private final AccessGuard guard;

    @Override
    public List<AppVO> list(String keyword) {
        LambdaQueryWrapper<App> query = new LambdaQueryWrapper<App>()
                .orderByAsc(App::getAppKey);
        if (keyword != null && !keyword.isBlank()) {
            query.and(w -> w.like(App::getName, keyword)
                    .or().like(App::getAppKey, keyword)
                    .or().like(App::getOwner, keyword));
        }
        return appMapper.selectList(query).stream().map(app -> {
            AppVO vo = new AppVO();
            vo.setAppKey(app.getAppKey());
            vo.setName(app.getName());
            vo.setOwner(app.getOwner());
            vo.setColor(app.getColor());
            vo.setEnvCount(Math.toIntExact(envMapper.selectCount(
                    new LambdaQueryWrapper<Env>().eq(Env::getAppId, app.getId()))));
            vo.setFlagCount(Math.toIntExact(countFlags(app.getId())));
            return vo;
        }).toList();
    }

    @Override
    @Transactional
    public App create(CreateAppDTO dto) {
        // 建应用没有所属环境，按平台角色判定；guard.require 传 appId=0 时只有平台 ADMIN 能过
        if (!"ADMIN".equals(guard.currentUser().getPlatformRole())) {
            throw BizException.forbidden("接入新应用需要平台管理员角色");
        }

        if (appMapper.selectCount(new LambdaQueryWrapper<App>().eq(App::getAppKey, dto.getAppKey())) > 0) {
            throw BizException.conflict("应用标识已存在：" + dto.getAppKey());
        }
        App app = new App();
        app.setAppKey(dto.getAppKey());
        app.setName(dto.getName());
        app.setOwner(dto.getOwner());
        app.setColor(dto.getColor() == null ? "#4f46e5" : dto.getColor());
        appMapper.insert(app);

        seedBuiltinRoles(app.getId());

        List<CreateEnvDTO> envs = dto.getEnvs() == null || dto.getEnvs().isEmpty()
                ? List.of(defaultEnv("development", "#6366f1", false),
                          defaultEnv("staging", "#f59e0b", false),
                          defaultEnv("production", "#10b981", true))
                : dto.getEnvs();
        envs.forEach(e -> envService.createIn(app, e));
        return app;
    }

    private CreateEnvDTO defaultEnv(String key, String color, boolean highRisk) {
        CreateEnvDTO e = new CreateEnvDTO();
        e.setEnvKey(key);
        e.setColor(color);
        e.setHighRisk(highRisk);
        return e;
    }

    private long countFlags(Long appId) {
        List<Env> envs = envMapper.selectList(new LambdaQueryWrapper<Env>().eq(Env::getAppId, appId));
        if (envs.isEmpty()) {
            return 0;
        }
        return flagMapper.selectCount(new LambdaQueryWrapper<FeatureFlag>()
                .in(FeatureFlag::getEnvId, envs.stream().map(Env::getId).toList()));
    }

    private void seedBuiltinRoles(Long appId) {
        for (String roleKey : BUILTIN_ROLES) {
            AppRole role = new AppRole();
            role.setAppId(appId);
            role.setRoleKey(roleKey);
            role.setName(switch (roleKey) {
                case "viewer" -> "只读";
                case "dev" -> "开发";
                case "release" -> "发布";
                default -> "应用管理员";
            });
            role.setBuiltin(true);
            roleMapper.insert(role);
            permsOf(roleKey).forEach(p -> {
                AppRolePerm rp = new AppRolePerm();
                rp.setRoleId(role.getId());
                rp.setPerm(p);
                rolePermMapper.insert(rp);
            });
        }
    }

    private List<String> permsOf(String roleKey) {
        return switch (roleKey) {
            case "viewer" -> List.of("flag:read");
            case "dev" -> List.of("flag:read", "flag:write", "value:nonprod", "instance:write");
            case "release" -> List.of("flag:read", "flag:write", "value:nonprod", "value:prod", "instance:write");
            default -> List.of("flag:read", "flag:write", "value:nonprod", "value:prod",
                    "instance:write", "env:manage", "member:manage");
        };
    }
}
