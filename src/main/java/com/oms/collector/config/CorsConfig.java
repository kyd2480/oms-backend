package com.oms.collector.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * CORS 설정
 * 
 * Frontend(Vite)가 Backend API를 호출할 수 있도록 허용
 */
@Configuration
public class CorsConfig {
    
    @Value("${cors.allowed-origins:http://localhost:5173,http://localhost:3000}")
    private String allowedOrigins;
    
    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        
        // 인증 정보 포함 허용
        config.setAllowCredentials(true);
        
        // 환경변수에서 허용할 Origin 읽기
        List<String> origins = new ArrayList<>();
        origins.add("http://localhost:5173");
        origins.add("http://localhost:3000");
        origins.add("http://127.0.0.1:5173");
        origins.add("http://127.0.0.1:3000");
        
        // 환경변수에서 추가 Origin 읽기
        if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
            for (String origin : allowedOrigins.split(",")) {
                if (!origin.trim().isEmpty()) {
                    origins.add(origin.trim());
                }
            }
        }
        
        config.setAllowedOrigins(origins);
        
        // 허용할 헤더
        config.setAllowedHeaders(List.of("*"));
        
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
