package com.oms.collector.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 전역 CORS 설정
 * - 모든 API 경로에 대해 Netlify 및 로컬 개발 허용
 * - 각 컨트롤러의 @CrossOrigin 보다 이 설정이 우선 적용됨
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOriginPatterns("*")   // 모든 출처 허용 (개발/배포 모두)
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
            .allowedHeaders("*")
            .allowCredentials(false)
            .maxAge(3600);
    }
}
