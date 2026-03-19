package com.oms.collector.controller;

import com.oms.collector.dto.CollectedOrder;
import com.oms.collector.dto.RawOrderDTO;
import com.oms.collector.entity.RawOrder;
import com.oms.collector.service.OrderCollectionService;
import com.oms.collector.service.RawOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 주문 수집 컨트롤러
 * 
 * 수동 주문 수집 및 상태 확인 API
 */
@Slf4j
@RestController
@RequestMapping("/api/collection")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CollectionController {
    
    private final OrderCollectionService collectionService;
    private final RawOrderService rawOrderService;
    
    /**
     * 전체 판매처 주문 수집 (수동)
     * 
     * POST /api/collection/collect-all
     */
    @PostMapping("/collect-all")
    public ResponseEntity<Map<String, Object>> collectAll(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate
    ) {
        log.info("📥 전체 판매처 주문 수집 요청");
        
        // 기본값: 최근 1시간
        if (startDate == null) {
            startDate = LocalDateTime.now().minusHours(1);
        }
        if (endDate == null) {
            endDate = LocalDateTime.now();
        }
        
        try {
            collectionService.collectAllChannels(startDate, endDate);
            
            // 미처리 주문 수 조회
            int unprocessedCount = rawOrderService.getUnprocessedOrders().size();
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "주문 수집 완료",
                "startDate", startDate.toString(),
                "endDate", endDate.toString(),
                "unprocessedOrders", unprocessedCount
            ));
            
        } catch (Exception e) {
            log.error("주문 수집 실패", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "주문 수집 실패: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 특정 판매처 주문 수집
     * 
     * POST /api/collection/collect/{channelCode}
     */
    @PostMapping("/collect/{channelCode}")
    public ResponseEntity<Map<String, Object>> collectByChannel(
        @PathVariable String channelCode,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate
    ) {
        log.info("📥 {} 주문 수집 요청", channelCode);
        
        if (startDate == null) {
            startDate = LocalDateTime.now().minusHours(1);
        }
        if (endDate == null) {
            endDate = LocalDateTime.now();
        }
        
        try {
            List<CollectedOrder> orders = collectionService.collectByChannel(channelCode, startDate, endDate);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", channelCode + " 주문 수집 완료",
                "channelCode", channelCode,
                "collectedCount", orders.size(),
                "orders", orders
            ));
            
        } catch (Exception e) {
            log.error("{} 주문 수집 실패", channelCode, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "주문 수집 실패: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Collector 상태 조회
     * 
     * GET /api/collection/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        log.info("📊 Collector 상태 조회");
        
        Map<String, String> collectorStatus = collectionService.getCollectorStatus();
        int unprocessedCount = rawOrderService.getUnprocessedOrders().size();
        
        return ResponseEntity.ok(Map.of(
            "collectors", collectorStatus,
            "unprocessedOrders", unprocessedCount,
            "timestamp", LocalDateTime.now()
        ));
    }
    
    /**
     * 미처리 주문 조회
     * 
     * GET /api/collection/unprocessed
     */
    @GetMapping("/unprocessed")
    @Transactional(readOnly = true)
    public ResponseEntity<List<RawOrderDTO>> getUnprocessedOrders() {
        log.info("📋 미처리 주문 조회");
        
        List<RawOrder> orders = rawOrderService.getUnprocessedOrders();
        
        // Entity → DTO 변환
        List<RawOrderDTO> dtos = orders.stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }
    
    /**
     * 판매처별 미처리 주문 조회
     * 
     * GET /api/collection/unprocessed/{channelCode}
     */
    @GetMapping("/unprocessed/{channelCode}")
    @Transactional(readOnly = true)
    public ResponseEntity<List<RawOrderDTO>> getUnprocessedOrdersByChannel(@PathVariable String channelCode) {
        log.info("📋 {} 미처리 주문 조회", channelCode);
        
        List<RawOrder> orders = rawOrderService.getUnprocessedOrdersByChannel(channelCode);
        
        // Entity → DTO 변환
        List<RawOrderDTO> dtos = orders.stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }
    
    /**
     * RawOrder Entity → DTO 변환
     */
    private RawOrderDTO convertToDTO(RawOrder entity) {
        return RawOrderDTO.builder()
            .rawOrderId(entity.getRawOrderId())
            .channelId(entity.getChannel().getChannelId())
            .channelCode(entity.getChannel().getChannelCode())
            .channelName(entity.getChannel().getChannelName())
            .channelOrderNo(entity.getChannelOrderNo())
            .rawData(entity.getRawData())
            .collectedAt(entity.getCollectedAt())
            .processed(entity.getProcessed())
            .processedAt(entity.getProcessedAt())
            .errorMessage(entity.getErrorMessage())
            .createdAt(entity.getCreatedAt())
            .build();
    }
    
    /**
     * 빠른 테스트: Mock 주문 생성
     * 
     * POST /api/collection/test
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> quickTest() {
        log.info("🧪 빠른 테스트 시작");
        
        try {
            LocalDateTime now = LocalDateTime.now();
            collectionService.collectAllChannels(now.minusMinutes(10), now);
            
            List<RawOrder> unprocessed = rawOrderService.getUnprocessedOrders();
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "테스트 완료",
                "unprocessedOrders", unprocessed.size(),
                "orders", unprocessed
            ));
            
        } catch (Exception e) {
            log.error("테스트 실패", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "테스트 실패: " + e.getMessage()
            ));
        }
    }
}
