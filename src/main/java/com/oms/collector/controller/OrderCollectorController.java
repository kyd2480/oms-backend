package com.oms.collector.controller;

import com.oms.collector.entity.SalesChannel;
import com.oms.collector.service.OrderCollectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Order Collector Controller
 * 
 * 주문 수집 관련 API
 */
@Slf4j
@RestController
@RequestMapping("/api/collector")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class OrderCollectorController {
    
    private final OrderCollectorService collectorService;
    
    /**
     * Health Check
     * 
     * GET /api/collector/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        log.info("Health check 요청");
        
        String status = collectorService.healthCheck();
        
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "order-collector-service",
            "message", status
        ));
    }
    
    /**
     * 활성화된 판매처 목록 조회
     * 
     * GET /api/collector/channels
     */
    @GetMapping("/channels")
    public ResponseEntity<List<SalesChannel>> getActiveChannels() {
        log.info("활성화된 판매처 목록 조회");
        
        List<SalesChannel> channels = collectorService.getActiveChannels();
        
        return ResponseEntity.ok(channels);
    }
    
    /**
     * 특정 판매처 조회
     * 
     * GET /api/collector/channels/{channelCode}
     */
    @GetMapping("/channels/{channelCode}")
    public ResponseEntity<SalesChannel> getChannel(@PathVariable String channelCode) {
        log.info("판매처 조회: {}", channelCode);
        
        SalesChannel channel = collectorService.getChannelByCode(channelCode);
        
        return ResponseEntity.ok(channel);
    }
    
    /**
     * 판매처 등록
     * 
     * POST /api/collector/channels
     */
    @PostMapping("/channels")
    public ResponseEntity<SalesChannel> registerChannel(@RequestBody SalesChannel channel) {
        log.info("판매처 등록 요청: {}", channel.getChannelCode());
        
        SalesChannel registered = collectorService.registerChannel(channel);
        
        return ResponseEntity.ok(registered);
    }
    
    /**
     * 판매처 상태 토글
     * 
     * PATCH /api/collector/channels/{channelCode}/toggle
     */
    @PatchMapping("/channels/{channelCode}/toggle")
    public ResponseEntity<Map<String, String>> toggleChannelStatus(@PathVariable String channelCode) {
        log.info("판매처 상태 변경: {}", channelCode);
        
        collectorService.toggleChannelStatus(channelCode);
        
        return ResponseEntity.ok(Map.of(
            "message", "판매처 상태가 변경되었습니다",
            "channelCode", channelCode
        ));
    }
    
    /**
     * Test API
     * 
     * GET /api/collector/test
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> test() {
        log.info("Test API 호출");
        
        return ResponseEntity.ok(Map.of(
            "message", "Order Collector Service is working!",
            "timestamp", System.currentTimeMillis(),
            "version", "1.0.0"
        ));
    }
}
