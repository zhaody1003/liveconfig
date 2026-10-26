package com.z.liveconfig.engine.rules;

import com.z.liveconfig.engine.EvalRequest;
import com.z.liveconfig.engine.FlagConfig;
import com.z.liveconfig.engine.RuleEvaluator;

import java.util.Optional;

/** 优先级最高：按服务器内网 IP 单独设值，仅用于主机金丝雀与单节点排障 */
public class InstanceOverrideRule implements RuleEvaluator {

    @Override
    public String name() {
        return "INSTANCE_OVERRIDE";
    }

    @Override
    public Optional<Decision> evaluate(EvalRequest request, FlagConfig flag) {
        if (!request.hasInstance()) {
            return Optional.empty();
        }
        String ip = request.instance().privateIp();
        String value = flag.overrideFor(ip);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(new Decision(value, name(),
                "服务器定向：" + ip + " 已设值 " + value, null));
    }
}
