package com.z.liveconfig.push;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HeartbeatTask {

    private final SseHub hub;

    @Scheduled(fixedDelayString = "${liveconfig.sse.heartbeat-ms:15000}")
    public void tick() {
        hub.heartbeat();
    }
}
