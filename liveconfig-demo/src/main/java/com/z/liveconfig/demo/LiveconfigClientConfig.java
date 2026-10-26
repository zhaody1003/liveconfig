package com.z.liveconfig.demo;

import com.z.liveconfig.sdk.LiveconfigClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Duration;

@Configuration
public class LiveconfigClientConfig {

    @Bean(destroyMethod = "close")
    public LiveconfigClient liveconfigClient(@Value("${liveconfig.endpoint}") String endpoint,
                                             @Value("${liveconfig.sdk-key}") String sdkKey,
                                             @Value("${liveconfig.app-name}") String appName,
                                             @Value("${liveconfig.timeout-ms:300}") long timeoutMs) {
        Path fallback = Path.of(System.getProperty("user.home"), ".liveconfig",
                appName + "-" + sdkKey.substring(0, Math.min(8, sdkKey.length())) + ".json");
        return LiveconfigClient.builder()
                .endpoint(endpoint)
                .sdkKey(sdkKey)
                .appName(appName)
                .timeout(Duration.ofMillis(timeoutMs))
                .fallbackFile(fallback)
                .build();
    }
}
