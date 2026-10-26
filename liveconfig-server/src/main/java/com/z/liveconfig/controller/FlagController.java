package com.z.liveconfig.controller;

import com.z.common.response.Result;
import com.z.liveconfig.dto.CreateFlagDTO;
import com.z.liveconfig.dto.SaveRulesDTO;
import com.z.liveconfig.dto.SetInstanceDTO;
import com.z.liveconfig.dto.UpdateDefaultDTO;
import com.z.liveconfig.service.IFlagService;
import com.z.liveconfig.service.IInstanceService;
import com.z.liveconfig.service.IRuleService;
import com.z.liveconfig.vo.FlagDetailVO;
import com.z.liveconfig.vo.FlagRowVO;
import com.z.liveconfig.vo.InstanceRowVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FlagController {

    private final IFlagService flagService;
    private final IRuleService ruleService;
    private final IInstanceService instanceService;
    private final com.z.liveconfig.service.ISdkEvaluateService evaluateService;
    private final com.z.liveconfig.security.AccessGuard guard;

    @GetMapping("/flags")
    public Result<List<FlagRowVO>> list(@RequestParam String appKey,
                                        @RequestParam String envKey,
                                        @RequestParam(required = false) String q) {
        return Result.success(flagService.list(appKey, envKey, q));
    }

    /** 分流调试：以登录身份 + 管理权限查任意环境的判定过程，不需要 SDK Key */
    @PostMapping("/flags/debug")
    public Result<com.z.liveconfig.engine.EvalResult> debug(@Valid @RequestBody com.z.liveconfig.dto.EvaluateDTO dto) {
        var app = guard.requireApp(dto.getAppKey());
        var env = guard.requireEnv(app.getId(), dto.getEnvKey());
        guard.require(app.getId(), env.getId(), com.z.liveconfig.security.Perm.FLAG_READ);
        return Result.success(evaluateService.evaluateConsole(env.getId(), dto));
    }

    @GetMapping("/flags/{flagId}")
    public Result<FlagDetailVO> detail(@PathVariable Long flagId) {
        return Result.success(flagService.detail(flagId));
    }

    @PostMapping("/flags")
    public Result<FlagDetailVO> create(@Valid @RequestBody CreateFlagDTO dto) {
        return Result.success(flagService.create(dto));
    }

    /** 铅笔按钮：改默认值并推送到该环境所有实例 */
    @PutMapping("/flags/{flagId}/default")
    public Result<FlagDetailVO> updateDefault(@PathVariable Long flagId,
                                              @Valid @RequestBody UpdateDefaultDTO dto) {
        return Result.success(flagService.updateDefault(flagId, dto));
    }

    @PutMapping("/flags/{flagId}/enabled")
    public Result<FlagDetailVO> toggle(@PathVariable Long flagId, @RequestParam boolean enabled) {
        return Result.success(flagService.toggle(flagId, enabled));
    }

    @GetMapping("/flags/{flagId}/rules")
    public Result<FlagDetailVO> rules(@PathVariable Long flagId) {
        return Result.success(flagService.detail(flagId));
    }

    @PutMapping("/flags/{flagId}/rules")
    public Result<FlagDetailVO> saveRules(@PathVariable Long flagId, @Valid @RequestBody SaveRulesDTO dto) {
        return Result.success(ruleService.save(flagId, dto));
    }

    @GetMapping("/flags/{flagId}/instances")
    public Result<List<InstanceRowVO>> instances(@PathVariable Long flagId) {
        return Result.success(instanceService.list(flagId));
    }

    @PutMapping("/flags/{flagId}/instances")
    public Result<Boolean> setInstance(@PathVariable Long flagId, @Valid @RequestBody SetInstanceDTO dto) {
        instanceService.set(flagId, dto);
        return Result.success();
    }

    @DeleteMapping("/flags/{flagId}/instances/{privateIp}")
    public Result<Boolean> clearInstance(@PathVariable Long flagId, @PathVariable String privateIp) {
        instanceService.clear(flagId, privateIp);
        return Result.success();
    }
}
