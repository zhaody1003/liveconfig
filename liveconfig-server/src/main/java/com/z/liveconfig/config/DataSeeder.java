package com.z.liveconfig.config;

import com.z.liveconfig.entity.App;
import com.z.liveconfig.entity.AppMember;
import com.z.liveconfig.entity.AppRole;
import com.z.liveconfig.entity.AppRolePerm;
import com.z.liveconfig.entity.Env;
import com.z.liveconfig.entity.FeatureFlag;
import com.z.liveconfig.entity.FlagChangeLog;
import com.z.liveconfig.entity.FlagInstanceOverride;
import com.z.liveconfig.entity.FlagRule;
import com.z.liveconfig.entity.PlatformUser;
import com.z.liveconfig.entity.SdkKey;
import com.z.liveconfig.mapper.AppMapper;
import com.z.liveconfig.mapper.AppMemberMapper;
import com.z.liveconfig.mapper.AppRoleMapper;
import com.z.liveconfig.mapper.AppRolePermMapper;
import com.z.liveconfig.mapper.EnvMapper;
import com.z.liveconfig.mapper.FeatureFlagMapper;
import com.z.liveconfig.mapper.FlagChangeLogMapper;
import com.z.liveconfig.mapper.FlagInstanceOverrideMapper;
import com.z.liveconfig.mapper.FlagRuleMapper;
import com.z.liveconfig.mapper.PlatformUserMapper;
import com.z.liveconfig.mapper.SdkKeyMapper;
import cn.hutool.crypto.digest.BCrypt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 演示数据种子，与 UI 原型对齐：3 应用 × 3 环境 × 7 开关 + 内置角色 + 成员矩阵。
 * 幂等：platform_user 表非空即跳过，重复启动不会插重复数据。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataSeeder {

    private final PlatformUserMapper userMapper;
    private final AppMapper appMapper;
    private final EnvMapper envMapper;
    private final SdkKeyMapper sdkKeyMapper;
    private final FeatureFlagMapper flagMapper;
    private final FlagRuleMapper ruleMapper;
    private final FlagInstanceOverrideMapper overrideMapper;
    private final AppRoleMapper roleMapper;
    private final AppRolePermMapper rolePermMapper;
    private final AppMemberMapper memberMapper;
    private final FlagChangeLogMapper changeLogMapper;

    @Bean
    ApplicationRunner seedDemoData() {
        return args -> {
            if (userMapper.selectCount(null) > 0) {
                log.info("已有数据，跳过种子");
                return;
            }
            seed();
            log.info("种子数据完成：{} 账号 / {} 应用 / {} 环境 / {} 开关 / {} 成员格",
                    userMapper.selectCount(null), appMapper.selectCount(null),
                    envMapper.selectCount(null), flagMapper.selectCount(null),
                    memberMapper.selectCount(null));
        };
    }

    @Transactional
    public void seed() {
        Map<String, PlatformUser> users = seedUsers();
        List<FeatureFlag> flags = seedApp("order-service", "订单服务", "交易团队", "#4f46e5", users);
        seedApp("user-service", "用户服务", "用户团队", "#059669", users);
        seedApp("rec-service", "推荐服务", "算法团队", "#f59e0b", users);
        seedCheckoutRulesAndHistory(flags);
    }

    private Map<String, PlatformUser> seedUsers() {
        Map<String, PlatformUser> byEmail = new LinkedHashMap<>();
        for (PlatformUser u : List.of(
                user("张三", "zhangsan@example.com", "ADMIN"),
                user("李四", "lisi@example.com", "USER"),
                user("王五", "wangwu@example.com", "USER"),
                user("赵六", "zhaoliu@example.com", "USER"))) {
            userMapper.insert(u);
            byEmail.put(u.getEmail(), u);
        }
        return byEmail;
    }

    private PlatformUser user(String name, String email, String platformRole) {
        PlatformUser u = new PlatformUser();
        u.setName(name);
        u.setEmail(email);
        u.setPasswordHash(BCrypt.hashpw("demo1234"));
        u.setPlatformRole(platformRole);
        u.setStatus("ACTIVE");
        return u;
    }

    /** 一个应用：环境 + 每个环境的 SDK Key + 内置角色 + 成员矩阵 + 开关 */
    private List<FeatureFlag> seedApp(String appKey, String appName, String owner, String color,
                                      Map<String, PlatformUser> users) {
        App app = new App();
        app.setAppKey(appKey);
        app.setName(appName);
        app.setOwner(owner);
        app.setColor(color);
        appMapper.insert(app);

        Map<String, Env> envs = new LinkedHashMap<>();
        envs.put("production", seedEnv(app, "production", "#10b981", true));
        envs.put("staging", seedEnv(app, "staging", "#f59e0b", false));
        envs.put("development", seedEnv(app, "development", "#6366f1", false));
        // 只有订单服务带演示用的额外环境，让「环境是可创建资源」这件事在界面上看得见
        if ("order-service".equals(app.getAppKey())) {
            Env gray = seedEnv(app, "gray", "#e11d48", true);
            envs.put("gray", gray);
            seedGrayFlag(gray);
        }

        Map<String, AppRole> roles = seedRoles(app, envs);
        seedMembers(app, envs, roles, users);

        return seedFlags(app, envs);
    }

    private Env seedEnv(App app, String envKey, String color, boolean highRisk) {
        Env env = new Env();
        env.setAppId(app.getId());
        env.setEnvKey(envKey);
        env.setColor(color);
        env.setHighRisk(highRisk);
        env.setConfigVersion(0L);
        envMapper.insert(env);

        for (String scope : List.of("SERVER", "CLIENT")) {
            String random = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            String plain = "lc-%s-%s-%s".formatted(scope.toLowerCase(), envKey.substring(0, 3), random);
            SdkKey key = new SdkKey();
            key.setEnvId(env.getId());
            key.setScope(scope);
            key.setKeyHash(sha256(plain));
            key.setKeyMasked(plain.substring(0, 8) + "…" + plain.substring(plain.length() - 4));
            sdkKeyMapper.insert(key);
        }
        return env;
    }

    private Map<String, AppRole> seedRoles(App app, Map<String, Env> envs) {
        Map<String, AppRole> roles = new LinkedHashMap<>();
        roles.put("viewer", seedRole(app, "viewer", "只读", List.of("flag:read")));
        roles.put("dev", seedRole(app, "dev", "开发",
                List.of("flag:read", "flag:write", "value:nonprod", "instance:write")));
        roles.put("release", seedRole(app, "release", "发布",
                List.of("flag:read", "flag:write", "value:nonprod", "value:prod", "instance:write")));
        roles.put("admin", seedRole(app, "admin", "应用管理员",
                List.of("flag:read", "flag:write", "value:nonprod", "value:prod",
                        "instance:write", "env:manage", "member:manage")));
        return roles;
    }

    private AppRole seedRole(App app, String roleKey, String name, List<String> perms) {
        AppRole role = new AppRole();
        role.setAppId(app.getId());
        role.setRoleKey(roleKey);
        role.setName(name);
        role.setBuiltin(true);
        roleMapper.insert(role);
        perms.forEach(p -> {
            AppRolePerm rp = new AppRolePerm();
            rp.setRoleId(role.getId());
            rp.setPerm(p);
            rolePermMapper.insert(rp);
        });
        return role;
    }

    private void seedMembers(App app, Map<String, Env> envs, Map<String, AppRole> roles,
                             Map<String, PlatformUser> users) {
        assign(app, envs, roles, users, "zhangsan@example.com", "production", "admin");
        assign(app, envs, roles, users, "zhangsan@example.com", "staging", "admin");
        assign(app, envs, roles, users, "zhangsan@example.com", "development", "dev");
        assign(app, envs, roles, users, "lisi@example.com", "production", "release");
        assign(app, envs, roles, users, "lisi@example.com", "staging", "dev");
        assign(app, envs, roles, users, "lisi@example.com", "development", "dev");
        assign(app, envs, roles, users, "wangwu@example.com", "production", "viewer");
        assign(app, envs, roles, users, "wangwu@example.com", "staging", "viewer");
        assign(app, envs, roles, users, "wangwu@example.com", "development", "dev");
    }

    private void assign(App app, Map<String, Env> envs, Map<String, AppRole> roles,
                        Map<String, PlatformUser> users, String email, String envKey, String roleKey) {
        AppMember m = new AppMember();
        m.setAppId(app.getId());
        m.setUserId(users.get(email).getId());
        m.setEnvId(envs.get(envKey).getId());
        m.setRoleId(roles.get(roleKey).getId());
        memberMapper.insert(m);
    }

    private List<FeatureFlag> seedFlags(App app, Map<String, Env> envs) {
        Env prod = envs.get("production");
        Env staging = envs.get("staging");
        Env dev = envs.get("development");

        List<FeatureFlag> created = new java.util.ArrayList<>();
        created.add(flag(prod, "new_checkout", "新结算页", "BOOLEAN", "false", true, 3));
        flag(prod, "max_upload_mb", "上传大小上限", "NUMBER", "10", true, 1);
        flag(prod, "promo_banner", "首页活动位", "JSON", "{\"img\":\"a.png\",\"height\":120}", true, 2);
        flag(staging, "promo_config", "活动配置", "JSON", "{\"x\":1}", false, 4);
        flag(staging, "new_checkout", "新结算页", "BOOLEAN", "true", true, 1);
        flag(dev, "legacy_banner", "旧版横幅", "BOOLEAN", "false", false, 1);
        flag(dev, "new_checkout", "新结算页", "BOOLEAN", "true", true, 2);
        return created;
    }

    private FeatureFlag flag(Env env, String key, String name, String type, String def,
                             boolean enabled, int version) {
        FeatureFlag f = new FeatureFlag();
        f.setEnvId(env.getId());
        f.setFlagKey(key);
        f.setName(name);
        f.setValueType(type);
        f.setDefaultValue(def);
        f.setEnabled(enabled);
        f.setVersion(version);
        flagMapper.insert(f);
        return f;
    }

    /** gray 环境带一个 SDK 已接入的开关，控制台与 demo 都能直接演示 */
    private void seedGrayFlag(Env gray) {
        FeatureFlag checkout = flag(gray, "checkout", "结算页", "BOOLEAN", "false", true, 1);
        rule(checkout, "WHITELIST", 0, "{\"userIds\":[\"1001\",\"1002\",\"test_admin\"]}", "true");
        rule(checkout, "BLACKLIST", 0, "{\"userIds\":[\"9527\"]}", "false");
        rule(checkout, "ATTRIBUTE", 0,
                "{\"conditions\":[{\"attr\":\"region\",\"op\":\"EQ\",\"value\":\"hangzhou\"}]}", "true");
        rule(checkout, "ROLLOUT", 0, "{\"percent\":40,\"bucketBy\":\"USER_ID\"}", "true");
        FlagInstanceOverride ov = new FlagInstanceOverride();
        ov.setFlagId(checkout.getId());
        ov.setPrivateIp("10.0.0.11");
        ov.setPublicIp("47.96.12.11");
        ov.setValue("true");
        overrideMapper.insert(ov);
        envMapper.bumpConfigVersion(gray.getId());
    }

    /** 给 order-service/production 的 new_checkout 配一套完整规则，供灰度页与分流调试直接使用 */
    private void seedCheckoutRulesAndHistory(List<FeatureFlag> seeded) {
        FeatureFlag checkout = seeded.get(0);
        Env prod = envMapper.selectById(checkout.getEnvId());

        rule(checkout, "WHITELIST", 0, "{\"userIds\":[\"1001\",\"1002\",\"test_admin\"]}", "true");
        rule(checkout, "BLACKLIST", 0, "{\"userIds\":[\"9527\"]}", "false");
        rule(checkout, "ATTRIBUTE", 0,
                "{\"conditions\":[{\"attr\":\"region\",\"op\":\"EQ\",\"value\":\"hangzhou\"}]}", "true");
        rule(checkout, "ROLLOUT", 0, "{\"percent\":30,\"bucketBy\":\"USER_ID\"}", "true");

        FlagInstanceOverride ov = new FlagInstanceOverride();
        ov.setFlagId(checkout.getId());
        ov.setPrivateIp("10.0.0.11");
        ov.setPublicIp("47.96.12.31");
        ov.setValue("true");
        overrideMapper.insert(ov);

        envMapper.bumpConfigVersion(prod.getId());
        Env reloaded = envMapper.selectById(prod.getId());

        change(reloaded, checkout.getFlagKey(), "zhangsan@example.com", "CREATE",
                null, "{\"defaultValue\":\"false\",\"valueType\":\"BOOLEAN\"}", 1L);
        change(reloaded, checkout.getFlagKey(), "zhangsan@example.com", "SAVE_RULES",
                null, "{\"whitelist\":[\"test_admin\"]}", 2L);
        change(reloaded, checkout.getFlagKey(), "lisi@example.com", "UPDATE_DEFAULT",
                "{\"rolloutPercent\":10}", "{\"rolloutPercent\":30}", 3L);
    }

    private void rule(FeatureFlag flag, String kind, int priority, String config, String value) {
        FlagRule r = new FlagRule();
        r.setFlagId(flag.getId());
        r.setKind(kind);
        r.setPriority(priority);
        r.setConfig(config);
        r.setValue(value);
        ruleMapper.insert(r);
    }

    private void change(Env env, String flagKey, String operator, String action,
                        String before, String after, long versionAtLog) {
        FlagChangeLog log = new FlagChangeLog();
        log.setEnvId(env.getId());
        log.setFlagKey(flagKey);
        // 显式指定演示用的操作人，自动填充遇到已有值不覆盖
        log.setOperator(operator);
        log.setAction(action);
        log.setBeforeJson(before);
        log.setAfterJson(after);
        log.setConfigVersion(versionAtLog);
        changeLogMapper.insert(log);
    }

    private static String sha256(String raw) {
        return cn.hutool.crypto.digest.DigestUtil.sha256Hex(raw);
    }
}
