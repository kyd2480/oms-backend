package com.oms.collector.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * CORS 설정
 * 
 * Frontend(Vite)가 Backend API를 호출할 수 있도록 허용
 */
@Configuration
public class CorsConfig {
    
    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        
        // 모든 Origin 허용 (프로덕션에서 작동하도록)
        config.addAllowedOriginPattern("*");
        
        // 인증 정보 포함 허용 (Origin Pattern 사용 시 false)
        config.setAllowCredentials(false);
        
        // 허용할 헤더
        config.addAllowedHeader("*");
        
        // 허용할 HTTP 메서드
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        
        // 노출할 헤더
        config.setExposedHeaders(List.of("Authorization", "Content-Type"));
        
        // Preflight 요청 캐시 시간 (초)
        config.setMaxAge(3600L);
        
        source.registerCorsConfiguration("/**", config);
        
        return new CorsFilter(source);
    }
}
