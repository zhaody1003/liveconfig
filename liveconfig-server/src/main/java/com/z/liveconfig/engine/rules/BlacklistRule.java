package com.z.liveconfig.engine.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.z.liveconfig.engine.RuleEvaluator;

/** 黑名单：命中强制排除，用于把出问题的用户挡在新逻辑外 */
public class BlacklistRule extends AbstractIdListRule {

    public BlacklistRule(ObjectMapper mapper) {
        super(mapper);
    }

    @Override
    protected String kind() {
        return "BLACKLIST";
    }

    @Override
    public String name() {
        return "BLACKLIST";
    }
}
