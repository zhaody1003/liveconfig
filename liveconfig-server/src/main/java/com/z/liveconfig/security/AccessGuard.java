package com.z.liveconfig.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.z.liveconfig.common.BizException;
import com.z.liveconfig.entity.App;
import com.z.liveconfig.entity.AppMember;
import com.z.liveconfig.entity.AppRole;
import com.z.liveconfig.entity.AppRolePerm;
import com.z.liveconfig.entity.Env;
import com.z.liveconfig.entity.FeatureFlag;
import com.z.liveconfig.entity.PlatformUser;
import com.z.liveconfig.mapper.AppMapper;
import com.z.liveconfig.mapper.AppMemberMapper;
import com.z.liveconfig.mapper.AppRoleMapper;
import com.z.liveconfig.mapper.AppRolePermMapper;
import com.z.liveconfig.mapper.EnvMapper;
import com.z.liveconfig.mapper.FeatureFlagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 权限判定。作用范围是「应用 + 环境」：同一人不同环境可以有不同角色。
 *
 * 高危环境（env.high_risk）不是靠环境名叫 prod 来判断的 —— 环境是可创建资源，
 * 名字随便起，风险等级是它的一个属性。
 */
@Component
@RequiredArgsConstructor
public class AccessGuard {

    private final AppMapper appMapper;
    private final EnvMapper envMapper;
    private final AppMemberMapper memberMapper;
    private final AppRoleMapper roleMapper;
    private final AppRolePermMapper rolePermMapper;
    private final FeatureFlagMapper flagMapper;

    public PlatformUser currentUser() {
        PlatformUser user = CurrentUser.get();
        if (user == null) {
            throw BizException.unauthorized("未登录");
        }
        return user;
    }

    public App requireApp(String appKey) {
        App app = appMapper.selectOne(new LambdaQueryWrapper<App>()
                .eq(App::getAppKey, appKey).last("LIMIT 1"));
        if (app == null) {
            throw BizException.notFound("应用不存在：" + appKey);
        }
        return app;
    }

    public App requireAppById(Long appId) {
        App app = appMapper.selectById(appId);
        if (app == null) {
            throw BizException.notFound("应用不存在：" + appId);
        }
        return app;
    }

    public Env requireEnv(Long appId, String envKey) {
        Env env = envMapper.selectOne(new LambdaQueryWrapper<Env>()
                .eq(Env::getAppId, appId).eq(Env::getEnvKey, envKey).last("LIMIT 1"));
        if (env == null) {
            throw BizException.notFound("环境不存在：" + envKey);
        }
        return env;
    }

    public FeatureFlag requireFlag(Long flagId) {
        FeatureFlag flag = flagMapper.selectById(flagId);
        if (flag == null) {
            throw BizException.notFound("开关不存在：" + flagId);
        }
        return flag;
    }

    public Env requireFlagEnv(FeatureFlag flag) {
        Env env = envMapper.selectById(flag.getEnvId());
        if (env == null) {
            throw BizException.notFound("开关所属环境已不存在");
        }
        return env;
    }

    /** 平台管理员放行全部；否则查该人在该应用该环境的角色权限集合 */
    public void require(Long appId, Long envId, Perm perm) {
        PlatformUser user = currentUser();
        if ("ADMIN".equals(user.getPlatformRole())) {
            return;
        }
        Set<String> perms = permsOf(appId, envId, user.getId());
        if (!perms.contains(perm.code())) {
            throw BizException.forbidden("缺少权限：" + perm.label());
        }
    }

    /**
     * 写值的实际权限点取决于环境风险等级。
     * 调用方只声明“我要改值”，不自己判断 prod。
     */
    public void requireWriteValue(Env env) {
        require(env.getAppId(), env.getId(),
                Boolean.TRUE.equals(env.getHighRisk()) ? Perm.VALUE_PROD : Perm.VALUE_NONPROD);
    }

    /** envId 为 null 表示应用级操作（列环境、建开关时定位环境），取该人在该应用任一环境的最强角色 */
    private Set<String> permsOf(Long appId, Long envId, Long userId) {
        LambdaQueryWrapper<AppMember> query = new LambdaQueryWrapper<AppMember>()
                .eq(AppMember::getAppId, appId)
                .eq(AppMember::getUserId, userId);
        if (envId != null) {
            query.eq(AppMember::getEnvId, envId);
        }
        List<AppMember> members = memberMapper.selectList(query);
        if (members.isEmpty()) {
            return Set.of();
        }
        List<Long> roleIds = members.stream().map(AppMember::getRoleId).distinct().toList();
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        List<AppRole> roles = roleMapper.selectList(new LambdaQueryWrapper<AppRole>()
                .in(AppRole::getId, roleIds));
        if (roles.isEmpty()) {
            return Set.of();
        }
        List<Long> validRoleIds = roles.stream().map(AppRole::getId).toList();
        List<AppRolePerm> rows = rolePermMapper.selectList(new LambdaQueryWrapper<AppRolePerm>()
                .in(AppRolePerm::getRoleId, validRoleIds));
        Set<String> perms = new HashSet<>();
        rows.forEach(r -> perms.add(r.getPerm()));
        return perms;
    }
}
