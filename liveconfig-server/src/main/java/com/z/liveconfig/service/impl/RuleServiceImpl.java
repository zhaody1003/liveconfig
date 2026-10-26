package com.z.liveconfig.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.z.liveconfig.common.BizException;
import com.z.liveconfig.common.ValueTypes;
import com.z.liveconfig.dto.SaveRulesDTO;
import com.z.liveconfig.entity.App;
import com.z.liveconfig.entity.Env;
import com.z.liveconfig.entity.FeatureFlag;
import com.z.liveconfig.entity.FlagRule;
import com.z.liveconfig.mapper.FlagRuleMapper;
import com.z.liveconfig.push.ConfigChangePublisher;
import com.z.liveconfig.security.AccessGuard;
import com.z.liveconfig.security.Perm;
import com.z.liveconfig.service.ConfigVersionService;
import com.z.liveconfig.service.IFlagService;
import com.z.liveconfig.service.IHistoryService;
import com.z.liveconfig.service.IRuleService;
import com.z.liveconfig.vo.FlagDetailVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** 规则 kind 与引擎流水线一一对应；服务器定向不在这里配，它在开关列表按 IP 设值 */
@Service
@RequiredArgsConstructor
public class RuleServiceImpl implements IRuleService {

    private static final List<String> KINDS = List.of("WHITELIST", "BLACKLIST", "ATTRIBUTE", "ROLLOUT");

    private final FlagRuleMapper ruleMapper;
    private final AccessGuard guard;
    private final ValueTypes valueTypes;
    private final ObjectMapper mapper;
    private final IFlagService flagService;
    private final IHistoryService history;
    private final ConfigVersionService versionService;
    private final ConfigChangePublisher publisher;

    @Override
    @Transactional
    public FlagDetailVO save(Long flagId, SaveRulesDTO dto) {
        FeatureFlag flag = guard.requireFlag(flagId);
        Env env = guard.requireFlagEnv(flag);
        App app = guard.requireAppById(env.getAppId());
        guard.require(app.getId(), env.getId(), Perm.FLAG_WRITE);

        for (SaveRulesDTO.RuleItem item : dto.getRules()) {
            if (!KINDS.contains(item.getKind())) {
                throw BizException.badRequest("未知规则类型：" + item.getKind() + "，可选 " + KINDS);
            }
            valueTypes.validate(flag.getValueType(), item.getValue());
            validateConfig(item);
        }

        List<FlagRule> before = ruleMapper.selectList(new LambdaQueryWrapper<FlagRule>()
                .eq(FlagRule::getFlagId, flagId));
        ruleMapper.delete(new LambdaQueryWrapper<FlagRule>().eq(FlagRule::getFlagId, flagId));
        for (SaveRulesDTO.RuleItem item : dto.getRules()) {
            FlagRule rule = new FlagRule();
            rule.setFlagId(flagId);
            rule.setKind(item.getKind());
            rule.setPriority(item.getPriority());
            rule.setConfig(item.getConfig());
            rule.setValue(item.getValue());
            ruleMapper.insert(rule);
        }

        Env bumped = versionService.bump(env);
        history.record(app, flag, guard.currentUser(), "SAVE_RULES",
                Map.of("count", before.size()), Map.of("count", dto.getRules().size()),
                bumped.getConfigVersion());
        publisher.published(bumped, app.getAppKey(), List.of(flag.getFlagKey()));
        return flagService.detail(flagId);
    }

    private void validateConfig(SaveRulesDTO.RuleItem item) {
        try {
            var node = mapper.readTree(item.getConfig());
            switch (item.getKind()) {
                case "WHITELIST", "BLACKLIST" -> requireArray(node, "userIds", item.getKind());
                case "ROLLOUT" -> {
                    if (!node.hasNonNull("percent")) {
                        throw BizException.badRequest("ROLLOUT 规则缺少 percent");
                    }
                    int percent = node.get("percent").asInt(-1);
                    if (percent < 0 || percent > 100) {
                        throw BizException.badRequest("percent 必须在 0~100，当前 " + percent);
                    }
                }
                case "ATTRIBUTE" -> requireArray(node, "conditions", item.getKind());
                default -> throw BizException.badRequest("未知规则类型：" + item.getKind());
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw BizException.badRequest("规则 config 不是合法 JSON：" + e.getMessage());
        }
    }

    private void requireArray(com.fasterxml.jackson.databind.JsonNode node, String field, String kind) {
        if (!node.hasNonNull(field) || !node.get(field).isArray() || node.get(field).isEmpty()) {
            throw BizException.badRequest(kind + " 规则的 " + field + " 不能为空数组");
        }
    }
}
