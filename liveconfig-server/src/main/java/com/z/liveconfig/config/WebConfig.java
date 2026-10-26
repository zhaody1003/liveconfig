package com.z.liveconfig.config;

import com.z.liveconfig.security.AuthInterceptor;
import com.z.liveconfig.security.SdkKeyInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final SdkKeyInterceptor sdkKeyInterceptor;

    @Value("${liveconfig.cors.allowed-origins:http://127.0.0.1:5173,http://localhost:5173}")
    private String[] allowedOrigins;

    /** 管理侧与 SDK 侧物理隔离：两套认证、两条拦截器，互不越界 */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login", "/api/ping");
        registry.addInterceptor(sdkKeyInterceptor)
                .addPathPatterns("/sdk/**");
    }

    /** 开发期走 Vite 代理不需要 CORS；独立部署或直连时按白名单放开 */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
