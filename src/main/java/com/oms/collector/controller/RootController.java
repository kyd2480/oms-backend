package com.oms.collector.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Root 경로 핸들러
 * 
 * Railway 도메인 접속 시 기본 정보 제공
 */
@RestController
public class RootController {
    
    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> root() {
        Map<String, Object> response = new HashMap<>();
        response.put("service", "Order Collector Service");
        response.put("version", "1.0.0");
        response.put("status", "running");
        response.put("endpoints", Map.of(
            "health", "/actuator/health",
            "stats", "/api/processing/stats",
            "orders", "/api/processing/orders",
            "collection", "/api/collection/stats"
        ));
        
        return ResponseEntity.ok(response);
    }
}
