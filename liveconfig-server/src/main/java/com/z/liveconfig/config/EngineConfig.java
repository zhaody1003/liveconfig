package com.z.liveconfig.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.z.liveconfig.engine.EvaluationEngine;
import com.z.liveconfig.engine.rules.AttributeRule;
import com.z.liveconfig.engine.rules.BlacklistRule;
import com.z.liveconfig.engine.rules.InstanceOverrideRule;
import com.z.liveconfig.engine.rules.RolloutRule;
import com.z.liveconfig.engine.rules.WhitelistRule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 引擎装配。
 *
 * engine 包里刻意一个 Spring 注解都没有：它是全系统唯一「算得准」的地方，
 * 保持纯 POJO 才能被纯单元测试覆盖，也才不会被下游误当成可扩展的框架。
 * 顺序即优先级，加一种手段是加一个类并插到这里。
 */
@Configuration
public class EngineConfig {

    @Bean
    public EvaluationEngine evaluationEngine(ObjectMapper mapper) {
        return new EvaluationEngine(List.of(
                new InstanceOverrideRule(),
                new WhitelistRule(mapper),
                new BlacklistRule(mapper),
                new AttributeRule(mapper),
                new RolloutRule(mapper)));
    }
}
