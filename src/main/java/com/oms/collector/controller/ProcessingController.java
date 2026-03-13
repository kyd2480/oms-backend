package com.oms.collector.controller;

import com.oms.collector.entity.Order;
import com.oms.collector.repository.OrderRepository;
import com.oms.collector.service.OrderProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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
    public ResponseEntity<OrderProcessingService.ProcessingStats> getStats() {
        log.info("📊 처리 통계 조회");

        OrderProcessingService.ProcessingStats stats = processingService.getStats();

        return ResponseEntity.ok(stats);
    }

    /**
     * 정규화된 주문 목록 조회 (페이지네이션)
     *
     * GET /api/processing/orders?page=0&size=50&sort=orderedAt,desc
     *
     * 응답 형식:
     * {
     *   "content": [...],
     *   "totalElements": 1234,
     *   "totalPages": 25,
     *   "number": 0,       // 현재 페이지 (0-based)
     *   "size": 50
     * }
     */
    @GetMapping("/orders")
    @Transactional(readOnly = true)
    public ResponseEntity<Page<Order>> getOrders(
        @RequestParam(defaultValue = "0")  int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        log.info("📋 정규화된 주문 조회 (page: {}, size: {})", page, size);

        // size 최대값 제한 (과부하 방지)
        int safeSize = Math.min(size, 100);

        Pageable pageable = PageRequest.of(page, safeSize, Sort.by(Sort.Direction.DESC, "orderedAt"));
        Page<Order> orderPage = orderRepository.findAll(pageable);

        // Lazy Loading 강제 초기화
        orderPage.getContent().forEach(order -> {
            order.getItems().size();
            if (order.getRawOrder() != null) {
                order.getRawOrder().getChannelOrderNo();
            }
            if (order.getChannel() != null) {
                order.getChannel().getChannelName();
            }
        });

        return ResponseEntity.ok(orderPage);
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
                order.getItems().size();
                if (order.getRawOrder() != null) {
                    order.getRawOrder().getChannelOrderNo();
                }
                if (order.getChannel() != null) {
                    order.getChannel().getChannelName();
                }
                return ResponseEntity.ok(order);
            })
            .orElse(ResponseEntity.notFound().build());
    }
}
