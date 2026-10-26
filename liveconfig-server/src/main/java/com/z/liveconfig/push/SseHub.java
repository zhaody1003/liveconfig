package com.z.liveconfig.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SSE 连接注册与广播。
 *
 * 在线 SDK 数的口径就是这里的活跃连接数：一个应用实例一条连接，
 * 单机跑多进程、同一宿主机跑多容器都算多个，所以它约等于机器数但不等于机器数。
 */
@Slf4j
@Component
public class SseHub {

    private record Entry(SseEmitter emitter, Long envId, String client) {
    }

    private final Map<Long, List<Entry>> byEnv = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong();

    public SseEmitter subscribe(Long envId, String client) {
        SseEmitter emitter = new SseEmitter(0L);
        Entry entry = new Entry(emitter, envId, client);
        byEnv.computeIfAbsent(envId, k -> new CopyOnWriteArrayList<>()).add(entry);
        emitter.onCompletion(() -> remove(entry));
        emitter.onTimeout(() -> remove(entry));
        emitter.onError(e -> remove(entry));
        log.debug("SSE 接入 env={} client={} 当前在线={}", envId, client, count(envId));
        return emitter;
    }

    public int count(Long envId) {
        List<Entry> entries = byEnv.get(envId);
        return entries == null ? 0 : entries.size();
    }

    /** 广播一个配置变更事件；发送失败的连接直接摘除，不拖垮整批 */
    public void broadcast(Long envId, String eventName, Object payload) {
        List<Entry> entries = byEnv.get(envId);
        if (entries == null || entries.isEmpty()) {
            return;
        }
        String id = String.valueOf(seq.incrementAndGet());
        for (Entry entry : List.copyOf(entries)) {
            try {
                entry.emitter().send(SseEmitter.event().id(id).name(eventName).data(payload));
            } catch (IOException | IllegalStateException e) {
                log.debug("SSE 发送失败，摘除连接 env={} client={}: {}", envId, entry.client(), e.getMessage());
                remove(entry);
            }
        }
    }

    public void heartbeat() {
        byEnv.forEach((envId, entries) -> broadcast(envId, "heartbeat", Map.of("ts", System.currentTimeMillis())));
    }

    private void remove(Entry entry) {
        List<Entry> entries = byEnv.get(entry.envId());
        if (entries != null) {
            entries.remove(entry);
        }
    }
}
