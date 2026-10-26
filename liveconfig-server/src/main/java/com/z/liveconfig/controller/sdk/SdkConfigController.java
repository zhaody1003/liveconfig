package com.z.liveconfig.controller.sdk;

import com.z.common.response.Result;
import com.z.liveconfig.dto.EvaluateDTO;
import com.z.liveconfig.engine.EvalResult;
import com.z.liveconfig.entity.Env;
import com.z.liveconfig.security.SdkKeyInterceptor;
import com.z.liveconfig.service.ISdkConfigService;
import com.z.liveconfig.service.ISdkEvaluateService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** SDK 专用接口：只读，认证走 X-SDK-Key */
@RestController
@RequestMapping("/sdk/v1")
@RequiredArgsConstructor
public class SdkConfigController {

    private final ISdkEvaluateService evaluateService;
    private final ISdkConfigService configService;

    @PostMapping("/evaluate")
    public Result<EvalResult> evaluate(@Valid @RequestBody EvaluateDTO dto, HttpServletRequest request) {
        return Result.success(evaluateService.evaluate(envId(request), dto));
    }

    @PostMapping("/evaluate/batch")
    public Result<List<EvalResult>> evaluateBatch(@RequestBody com.z.liveconfig.dto.EvaluateBatchDTO dto,
                                                  HttpServletRequest request) {
        EvaluateDTO single = new EvaluateDTO();
        single.setUserId(dto.getUserId());
        single.setInstance(dto.getInstance());
        single.setAttributes(dto.getAttributes());
        single.setFlagKey(dto.getFlagKeys().isEmpty() ? "" : dto.getFlagKeys().get(0));
        return Result.success(evaluateService.evaluateBatch(envId(request), dto.getFlagKeys(), single));
    }

    @GetMapping("/config")
    public Result<Map<String, Object>> config(HttpServletRequest request) {
        return Result.success(SseStreamController.slim(
                configService.snapshot(envId(request))));
    }

    private Long envId(HttpServletRequest request) {
        return ((Env) request.getAttribute(SdkKeyInterceptor.ATTR_ENV)).getId();
    }
}
