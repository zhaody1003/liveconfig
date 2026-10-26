package com.z.liveconfig.demo;

import com.z.common.response.Result;
import com.z.liveconfig.sdk.FlagContext;
import com.z.liveconfig.sdk.FlagDecision;
import com.z.liveconfig.sdk.LiveconfigClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 业务代码里只出现这一种用法：两条路都在代码里，走哪条由配置决定。
 * 改配置不用重新编译、不用发版、不用重启这个进程。
 */
@RestController
@RequiredArgsConstructor
public class CheckoutController {

    private static final String FLAG = "checkout";

    private final LiveconfigClient liveconfig;

    @GetMapping("/checkout")
    public String checkout(@RequestParam String userId) {
        if (liveconfig.getBoolean(FLAG, userId, false)) {
            return "新结算页 · 用户 " + userId;
        }
        return "老结算页 · 用户 " + userId;
    }

    /** 把判定过程摊开看：命中哪条规则、桶号多少、值从哪条路径来 */
    @GetMapping("/explain")
    public Result<Map<String, Object>> explain(@RequestParam String userId,
                                               @RequestParam(defaultValue = "hangzhou") String region) {
        FlagDecision decision = liveconfig.explain(FLAG,
                FlagContext.ofUser(userId, Map.of("region", region)));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("flagKey", decision.key());
        data.put("value", decision.value());
        data.put("hitRule", decision.hitRule());
        data.put("reason", decision.reason());
        data.put("bucket", decision.bucket());
        data.put("source", decision.source());
        data.put("configVersion", decision.configVersion());
        return Result.success(data);
    }
}
