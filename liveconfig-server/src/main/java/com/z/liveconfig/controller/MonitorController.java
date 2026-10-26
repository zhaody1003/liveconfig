package com.z.liveconfig.controller;

import com.z.common.response.Result;
import com.z.liveconfig.entity.Env;
import com.z.liveconfig.mapper.EnvMapper;
import com.z.liveconfig.push.SseHub;
import com.z.liveconfig.security.AccessGuard;
import com.z.liveconfig.security.Perm;
import com.z.liveconfig.vo.MonitorOverviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/monitor")
@RequiredArgsConstructor
public class MonitorController {

    private final SseHub hub;
    private final EnvMapper envMapper;
    private final AccessGuard guard;

    @GetMapping("/overview")
    public Result<MonitorOverviewVO> overview(@RequestParam String appKey, @RequestParam String envKey) {
        com.z.liveconfig.entity.App app = guard.requireApp(appKey);
        Env env = guard.requireEnv(app.getId(), envKey);
        guard.require(app.getId(), env.getId(), Perm.FLAG_READ);

        MonitorOverviewVO vo = new MonitorOverviewVO();
        vo.setAppKey(appKey);
        vo.setEnvKey(envKey);
        vo.setConfigVersion(env.getConfigVersion());
        vo.setOnlineSdk(hub.count(env.getId()));
        return Result.success(vo);
    }

    /** 浏览器监控页的事件流。EventSource 不能带自定义头，token 走 query，由 AuthInterceptor 兼容 */
    @GetMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter stream(@RequestParam String appKey, @RequestParam String envKey) {
        com.z.liveconfig.entity.App app = guard.requireApp(appKey);
        Env env = guard.requireEnv(app.getId(), envKey);
        guard.require(app.getId(), env.getId(), Perm.FLAG_READ);

        SseEmitter emitter = hub.subscribe(env.getId(), "console:" + guard.currentUser().getEmail());
        try {
            emitter.send(SseEmitter.event()
                    .name("hello")
                    .data(Map.of("appKey", appKey, "envKey", envKey,
                            "configVersion", env.getConfigVersion(), "role", "CONSOLE")));
        } catch (Exception e) {
            return emitter;
        }
        return emitter;
    }
}
