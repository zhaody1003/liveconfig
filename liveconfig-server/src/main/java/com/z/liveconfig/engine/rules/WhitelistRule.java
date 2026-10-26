package com.z.liveconfig.engine.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.z.liveconfig.engine.RuleEvaluator;

/** 白名单优先于黑名单：先强制命中，再强制排除 */
public class WhitelistRule extends AbstractIdListRule {

    public WhitelistRule(ObjectMapper mapper) {
        super(mapper);
    }

    @Override
    protected String kind() {
        return "WHITELIST";
    }

    @Override
    public String name() {
        return "WHITELIST";
    }
}
