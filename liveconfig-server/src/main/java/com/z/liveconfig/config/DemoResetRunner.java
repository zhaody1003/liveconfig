package com.z.liveconfig.config;

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
import com.z.liveconfig.mapper.SdkInstanceMapper;
import com.z.liveconfig.mapper.SdkKeyMapper;
import com.z.liveconfig.mapper.UserSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 演示环境重置：-Dliveconfig.demo.reset=true 时清空业务表并重新播种。讲课时用，默认关闭。
 *
 * Order 排在 DataSeeder 之后，两者在各自事务里跑；这里先清后种，结果与首次启动一致。
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class DemoResetRunner implements ApplicationRunner {

    private final DataSeeder seeder;
    private final PlatformUserMapper userMapper;
    private final AppMemberMapper memberMapper;
    private final AppRolePermMapper rolePermMapper;
    private final AppRoleMapper roleMapper;
    private final SdkKeyMapper sdkKeyMapper;
    private final SdkInstanceMapper instanceMapper;
    private final FlagRuleMapper ruleMapper;
    private final FlagInstanceOverrideMapper overrideMapper;
    private final FeatureFlagMapper flagMapper;
    private final EnvMapper envMapper;
    private final AppMapper appMapper;
    private final FlagChangeLogMapper changeLogMapper;
    private final UserSessionMapper sessionMapper;

    @org.springframework.beans.factory.annotation.Value("${liveconfig.demo.reset:false}")
    private boolean reset;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!reset) {
            return;
        }
        // 删除顺序按外键依赖来：先叶子表，最后才是被引用的 platform_user / app / env
        overrideMapper.delete(null);
        ruleMapper.delete(null);
        changeLogMapper.delete(null);
        flagMapper.delete(null);
        sessionMapper.delete(null);
        memberMapper.delete(null);
        rolePermMapper.delete(null);
        roleMapper.delete(null);
        sdkKeyMapper.delete(null);
        instanceMapper.delete(null);
        envMapper.delete(null);
        appMapper.delete(null);
        userMapper.delete(null);
        seeder.seed();
        log.info("演示数据已重置");
    }
}
