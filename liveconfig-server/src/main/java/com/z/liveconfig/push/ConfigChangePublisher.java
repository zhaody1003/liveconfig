package com.z.liveconfig.push;

import com.z.liveconfig.entity.Env;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;

/**
 * 配置变更广播。
 *
 * 关键点是**事务提交之后**才发：如果在事务内广播，SDK 收到事件回头拉 /sdk/v1/config
 * 时事务还没提交，拿到的是旧配置，缓存反而被写脏。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigChangePublisher {

    private final SseHub hub;

    public void published(Env env, String appKey, List<String> changedKeys) {
        Map<String, Object> payload = Map.of(
                "appKey", appKey,
                "envKey", env.getEnvKey(),
                "configVersion", env.getConfigVersion(),
                "changedKeys", changedKeys);

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send(env.getId(), payload);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send(env.getId(), payload);
            }
        });
    }

    private void send(Long envId, Map<String, Object> payload) {
        log.debug("广播 config-change env={} payload={}", envId, payload);
        hub.broadcast(envId, "config-change", payload);
    }
}
