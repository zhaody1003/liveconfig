package com.z.liveconfig.controller.sdk;

import com.z.liveconfig.engine.EnvConfigSnapshot;
import com.z.liveconfig.entity.App;
import com.z.liveconfig.entity.Env;
import com.z.liveconfig.push.SseHub;
import com.z.liveconfig.security.SdkKeyInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SDK 的 SSE 订阅入口。
 *
 * 事件 id 用环境的 configVersion：SDK 断线重连时带 Last-Event-ID，
 * 服务端在 hello 里回当前版本，SDK 据此判断要不要全量重拉快照。
 */
@RestController
@RequestMapping("/sdk/v1")
@RequiredArgsConstructor
public class SseStreamController {

    private final SseHub hub;

    @GetMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter stream(HttpServletRequest request,
                             @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        Env env = (Env) request.getAttribute(SdkKeyInterceptor.ATTR_ENV);
        App app = (App) request.getAttribute(SdkKeyInterceptor.ATTR_APP);
        String client = app.getAppKey() + "@" + request.getRemoteAddr();

        SseEmitter emitter = hub.subscribe(env.getId(), client);
        Map<String, Object> hello = new LinkedHashMap<>();
        hello.put("appKey", app.getAppKey());
        hello.put("envKey", env.getEnvKey());
        hello.put("configVersion", env.getConfigVersion());
        hello.put("resumedFrom", lastEventId);
        try {
            emitter.send(SseEmitter.event()
                    .id(String.valueOf(env.getConfigVersion()))
                    .name("hello")
                    .data(hello));
        } catch (Exception e) {
            return emitter;
        }
        return emitter;
    }

    /** 快照瘦身：只回 SDK 兜底需要的字段，避免把 id 之类内部信息漏给下游 */
    static Map<String, Object> slim(EnvConfigSnapshot snapshot) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("appKey", snapshot.appKey());
        root.put("envKey", snapshot.envKey());
        root.put("configVersion", snapshot.configVersion());
        List<Map<String, Object>> flags = snapshot.flags().stream().map(f -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("flagKey", f.flagKey());
            item.put("valueType", f.valueType());
            item.put("defaultValue", f.defaultValue());
            item.put("enabled", f.enabled());
            item.put("version", f.version());
            item.put("rules", f.rules().stream().map(r -> Map.of(
                    "kind", r.kind(), "priority", r.priority(),
                    "config", r.config(), "value", r.value())).toList());
            item.put("overrides", f.instanceOverrides());
            return item;
        }).toList();
        root.put("flags", flags);
        return root;
    }
}
