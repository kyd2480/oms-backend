package com.oms.collector.controller;

import com.oms.collector.entity.Order;
import com.oms.collector.entity.OrderItem;
import com.oms.collector.entity.SalesChannel;
import com.oms.collector.repository.OrderRepository;
import com.oms.collector.repository.SalesChannelRepository;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
    private final SalesChannelRepository salesChannelRepository;

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

    /**
     * 수동 주문 저장 (직접입력 / CSV 업로드)
     * POST /api/processing/orders
     *
     * Body: [{
     *   orderNo, channel, receiverName, receiverPhone, address,
     *   productName, sku, barcode, optionName, quantity, salePrice, memo
     * }]
     */
    @PostMapping("/orders")
    @Transactional
    public ResponseEntity<Map<String, Object>> saveManualOrders(
        @RequestBody List<Map<String, Object>> orderList
    ) {
        log.info("수동 주문 저장 요청: {}건", orderList.size());

        // DIRECT 채널 조회 (없으면 null 허용)
        SalesChannel directChannel = null;
        try {
            directChannel = salesChannelRepository.findByChannelCode("DIRECT").orElse(null);
        } catch (Exception ignored) {}

        int saved = 0;
        int skipped = 0;

        for (Map<String, Object> raw : orderList) {
            try {
                String orderNo = (String) raw.get("orderNo");

                // 중복 주문번호 스킵
                if (orderNo != null && orderRepository.findByOrderNo(orderNo).isPresent()) {
                    skipped++;
                    continue;
                }

                // orderNo 없으면 자동 생성
                if (orderNo == null || orderNo.isBlank()) {
                    orderNo = "MANUAL-" + LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                        + "-" + (saved + 1);
                }

                // 채널 결정
                SalesChannel channel = directChannel;
                String channelCode = (String) raw.get("channel");
                if (channelCode != null && !channelCode.isBlank()
                        && !"직접입력".equals(channelCode)) {
                    try {
                        channel = salesChannelRepository
                            .findByChannelCode(channelCode).orElse(directChannel);
                    } catch (Exception ignored) {}
                }

                // 금액
                BigDecimal salePrice = BigDecimal.ZERO;
                Object priceObj = raw.get("salePrice");
                if (priceObj != null) {
                    try { salePrice = new BigDecimal(priceObj.toString()); }
                    catch (Exception ignored) {}
                }

                // 수량
                int quantity = 1;
                Object qtyObj = raw.get("quantity");
                if (qtyObj != null) {
                    try { quantity = Integer.parseInt(qtyObj.toString()); }
                    catch (Exception ignored) {}
                }

                // Order 생성
                Order order = Order.builder()
                    .orderNo(orderNo)
                    .channel(channel)
                    .channelOrderNo(orderNo)
                    .customerName((String) raw.getOrDefault("receiverName", ""))
                    .customerPhone((String) raw.getOrDefault("receiverPhone", ""))
                    .recipientName((String) raw.getOrDefault("receiverName", ""))
                    .recipientPhone((String) raw.getOrDefault("receiverPhone", ""))
                    .address((String) raw.getOrDefault("address", ""))
                    .totalAmount(salePrice.multiply(BigDecimal.valueOf(quantity)))
                    .paymentAmount(salePrice.multiply(BigDecimal.valueOf(quantity)))
                    .orderStatus(Order.OrderStatus.PENDING)
                    .paymentStatus(Order.PaymentStatus.PENDING)
                    .orderedAt(LocalDateTime.now())
                    .build();

                // OrderItem 생성
                String productName = (String) raw.getOrDefault("productName", "");
                if (!productName.isBlank()) {
                    OrderItem item = OrderItem.builder()
                        .productCode((String) raw.getOrDefault("sku", ""))
                        .channelProductCode((String) raw.getOrDefault("barcode", ""))
                        .productName(productName)
                        .optionName((String) raw.getOrDefault("optionName", ""))
                        .quantity(quantity)
                        .unitPrice(salePrice)
                        .totalPrice(salePrice.multiply(BigDecimal.valueOf(quantity)))
                        .build();
                    order.addItem(item);
                }

                orderRepository.save(order);
                saved++;

            } catch (Exception e) {
                log.error("수동 주문 저장 실패: {}", e.getMessage());
                skipped++;
            }
        }

        log.info("수동 주문 저장 완료: {}건 저장, {}건 스킵", saved, skipped);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "saved", saved,
            "skipped", skipped,
            "message", saved + "건 저장 완료" + (skipped > 0 ? " (" + skipped + "건 스킵)" : "")
        ));
    }

}