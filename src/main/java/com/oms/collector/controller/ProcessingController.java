package com.oms.collector.controller;

import com.oms.collector.entity.Order;
import com.oms.collector.repository.OrderRepository;
import com.oms.collector.service.OrderProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 주문 처리 컨트롤러
 * 
 * 주문 정규화 및 조회 API
 */
@Slf4j
@RestController
@RequestMapping("/api/processing")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProcessingController {
    
    private final OrderProcessingService processingService;
    private final OrderRepository orderRepository;
    
    /**
     * 미처리 주문 일괄 처리
     * 
     * POST /api/processing/process-all
     */
    @PostMapping("/process-all")
    public ResponseEntity<Map<String, Object>> processAll() {
        log.info("📥 미처리 주문 일괄 처리 요청");
        
        try {
            int processedCount = processingService.processUnprocessedOrders();
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "주문 처리 완료",
                "processedCount", processedCount
            ));
            
        } catch (Exception e) {
            log.error("주문 처리 실패", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "주문 처리 실패: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 특정 판매처 미처리 주문 처리
     * 
     * POST /api/processing/process/{channelCode}
     */
    @PostMapping("/process/{channelCode}")
    public ResponseEntity<Map<String, Object>> processByChannel(@PathVariable String channelCode) {
        log.info("📥 {} 미처리 주문 처리 요청", channelCode);
        
        try {
            int processedCount = processingService.processUnprocessedOrdersByChannel(channelCode);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", channelCode + " 주문 처리 완료",
                "channelCode", channelCode,
                "processedCount", processedCount
            ));
            
        } catch (Exception e) {
            log.error("{} 주문 처리 실패", channelCode, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "주문 처리 실패: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 처리 통계 조회
     * 
     * GET /api/processing/stats
     */
    @GetMapping("/stats")
    @Transactional(readOnly = true)
    public ResponseEntity<java.util.Map<String, Object>> getStats(
        @RequestParam(required = false) String startDate,
        @RequestParam(required = false) String endDate
    ) {
        log.info("📊 처리 통계 조회: {} ~ {}", startDate, endDate);

        LocalDateTime start = startDate != null
            ? LocalDate.parse(startDate).atStartOfDay()
            : LocalDate.now().minusDays(6).atStartOfDay();
        LocalDateTime end = endDate != null
            ? LocalDate.parse(endDate).atTime(23, 59, 59)
            : LocalDateTime.now();

        // 날짜 범위 주문 조회
        List<Order> orders = orderRepository.findAll().stream()
            .filter(o -> o.getOrderedAt() != null
                      && !o.getOrderedAt().isBefore(start)
                      && !o.getOrderedAt().isAfter(end))
            .toList();

        orders.forEach(o -> {
            o.getItems().size();
            if (o.getChannel() != null) o.getChannel().getChannelName();
        });

        long totalOrders  = orders.size();
        long shipped      = orders.stream().filter(o -> o.getOrderStatus() == Order.OrderStatus.SHIPPED
                                                     || o.getOrderStatus() == Order.OrderStatus.DELIVERED).count();
        long canceled     = orders.stream().filter(o -> o.getOrderStatus() == Order.OrderStatus.CANCELLED).count();
        long confirmed    = orders.stream().filter(o -> o.getOrderStatus() == Order.OrderStatus.CONFIRMED).count();
        long pending      = orders.stream().filter(o -> o.getOrderStatus() == Order.OrderStatus.PENDING).count();

        // 판매처별
        java.util.Map<String, Long> channelMap = new java.util.LinkedHashMap<>();
        for (Order o : orders) {
            String ch = o.getChannel() != null ? o.getChannel().getChannelName() : "기타";
            channelMap.merge(ch, 1L, Long::sum);
        }
        var byChannel = channelMap.entrySet().stream()
            .map(e -> java.util.Map.of("channelName", e.getKey(), "orderCount", e.getValue(), "amount", 0))
            .toList();

        var byStatus = java.util.List.of(
            java.util.Map.of("status", "결제완료",  "count", pending),
            java.util.Map.of("status", "출고대기",  "count", confirmed),
            java.util.Map.of("status", "출고완료",  "count", shipped),
            java.util.Map.of("status", "취소/반품", "count", canceled)
        );

        return ResponseEntity.ok(java.util.Map.of(
            "totalOrders",  totalOrders,
            "totalAmount",  0,
            "shipped",      shipped,
            "canceled",     canceled,
            "byChannel",    byChannel,
            "byStatus",     byStatus,
            "startDate",    start.toString(),
            "endDate",      end.toString()
        ));
    }
    
    /**
     * 정규화된 주문 목록 조회
     * 
     * GET /api/processing/orders
     */
    @GetMapping("/orders")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Order>> getOrders(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        log.info("📋 정규화된 주문 조회 (page: {}, size: {})", page, size);
        
        // TODO: Pagination 구현
        List<Order> orders = orderRepository.findAll();
        
        // Lazy Loading 강제 초기화
        orders.forEach(order -> {
            order.getItems().size(); // items 강제 로딩
            if (order.getRawOrder() != null) {
                order.getRawOrder().getChannelOrderNo(); // rawOrder 강제 로딩
            }
            if (order.getChannel() != null) {
                order.getChannel().getChannelName(); // channel 강제 로딩
            }
        });
        
        return ResponseEntity.ok(orders);
    }
    
    /**
     * 오늘 주문 조회
     * 
     * GET /api/processing/orders/today
     */
    @GetMapping("/orders/today")
    public ResponseEntity<Map<String, Object>> getTodayOrders() {
        log.info("📋 오늘 주문 조회");
        
        long todayCount = orderRepository.countTodayOrders();
        // TODO: 실제 주문 목록 조회
        
        return ResponseEntity.ok(Map.of(
            "todayCount", todayCount,
            "message", "오늘 주문: " + todayCount + " 건"
        ));
    }
    
    /**
     * 주문 상세 조회
     * 
     * GET /api/processing/orders/{orderNo}
     */
    @GetMapping("/orders/{orderNo}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getOrder(@PathVariable String orderNo) {
        log.info("📋 주문 상세 조회: {}", orderNo);
        
        return orderRepository.findByOrderNo(orderNo)
            .map(order -> {
                order.getItems().size(); // items 강제 로딩
                if (order.getRawOrder() != null) {
                    order.getRawOrder().getChannelOrderNo(); // rawOrder 강제 로딩
                }
                if (order.getChannel() != null) {
                    order.getChannel().getChannelName(); // channel 강제 로딩
                }
                return ResponseEntity.ok(order);
            })
            .orElse(ResponseEntity.notFound().build());
    }
}
