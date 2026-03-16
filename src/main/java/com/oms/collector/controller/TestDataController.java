package com.oms.collector.controller;

import com.oms.collector.entity.Order;
import com.oms.collector.entity.OrderItem;
import com.oms.collector.entity.SalesChannel;
import com.oms.collector.repository.OrderRepository;
import com.oms.collector.repository.SalesChannelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * ⚠️ 테스트 전용 컨트롤러
 *
 * 재고 기반 더미 주문 데이터 삽입용
 * 실제 쇼핑몰 API 연결 후 이 파일 삭제
 *
 * POST /api/test/orders/insert  - 테스트 주문 삽입
 * DELETE /api/test/orders/clear - 테스트 주문 전체 삭제
 */
@Slf4j
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TestDataController {

    private final OrderRepository       orderRepository;
    private final SalesChannelRepository salesChannelRepository;

    /**
     * 테스트 주문 삽입
     * POST /api/test/orders/insert
     *
     * Body: [{
     *   productName, optionName, barcode, quantity,
     *   receiverName, receiverPhone, address, channel, salePrice
     * }]
     */
    @PostMapping("/orders/insert")
    @Transactional
    public ResponseEntity<Map<String, Object>> insertTestOrders(
        @RequestBody List<Map<String, Object>> orderList
    ) {
        log.info("⚠️ 테스트 주문 삽입: {}건", orderList.size());

        int saved   = 0;
        int skipped = 0;
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        for (Map<String, Object> raw : orderList) {
            try {
                // 자동 주문번호 생성
                String orderNo = "TEST-" + today + "-" + String.format("%04d", saved + 1);

                // 중복 체크
                if (orderRepository.findByOrderNo(orderNo).isPresent()) {
                    skipped++;
                    continue;
                }

                // 채널 조회
                String channelName = (String) raw.getOrDefault("channel", "스마트스토어");
                SalesChannel channel = findChannel(channelName);

                // 금액
                BigDecimal price = BigDecimal.ZERO;
                Object priceObj  = raw.get("salePrice");
                if (priceObj != null) {
                    try { price = new BigDecimal(priceObj.toString()); }
                    catch (Exception ignored) {}
                }

                int qty = 1;
                Object qtyObj = raw.get("quantity");
                if (qtyObj != null) {
                    try { qty = Integer.parseInt(qtyObj.toString()); }
                    catch (Exception ignored) {}
                }

                // 주문 생성
                Order order = Order.builder()
                    .orderNo(orderNo)
                    .channel(channel)
                    .channelOrderNo("CH-" + orderNo)
                    .customerName((String) raw.getOrDefault("receiverName", "테스트"))
                    .customerPhone((String) raw.getOrDefault("receiverPhone", "010-0000-0000"))
                    .recipientName((String) raw.getOrDefault("receiverName", "테스트"))
                    .recipientPhone((String) raw.getOrDefault("receiverPhone", "010-0000-0000"))
                    .address((String) raw.getOrDefault("address", "테스트 주소"))
                    .totalAmount(price.multiply(BigDecimal.valueOf(qty)))
                    .paymentAmount(price.multiply(BigDecimal.valueOf(qty)))
                    .orderStatus(Order.OrderStatus.PENDING)
                    .paymentStatus(Order.PaymentStatus.PAID)
                    .orderedAt(LocalDateTime.now().minusMinutes(new Random().nextInt(1440)))
                    .build();

                // 상품 아이템
                String productName = (String) raw.getOrDefault("productName", "");
                String optionName  = (String) raw.getOrDefault("optionName",  "");
                String barcode     = (String) raw.getOrDefault("barcode",     "");

                OrderItem item = OrderItem.builder()
                    .productCode(barcode)          // 바코드를 productCode로 사용
                    .channelProductCode(barcode)
                    .productName(productName + (optionName.isBlank() ? "" : " / " + optionName))
                    .optionName(optionName)
                    .quantity(qty)
                    .unitPrice(price)
                    .totalPrice(price.multiply(BigDecimal.valueOf(qty)))
                    .build();

                order.addItem(item);
                orderRepository.save(order);
                saved++;

            } catch (Exception e) {
                log.error("테스트 주문 삽입 실패: {}", e.getMessage());
                skipped++;
            }
        }

        log.info("테스트 주문 삽입 완료: {}건 저장, {}건 스킵", saved, skipped);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "saved",   saved,
            "skipped", skipped,
            "message", saved + "건 테스트 주문 삽입 완료"
        ));
    }

    /**
     * 테스트 주문 전체 삭제 (TEST- 로 시작하는 주문만)
     * DELETE /api/test/orders/clear
     */
    @DeleteMapping("/orders/clear")
    @Transactional
    public ResponseEntity<Map<String, Object>> clearTestOrders() {
        log.info("⚠️ 테스트 주문 삭제");

        List<Order> testOrders = orderRepository.findAll().stream()
            .filter(o -> o.getOrderNo() != null && o.getOrderNo().startsWith("TEST-"))
            .toList();

        orderRepository.deleteAll(testOrders);
        log.info("테스트 주문 {}건 삭제", testOrders.size());

        return ResponseEntity.ok(Map.of(
            "success", true,
            "deleted", testOrders.size(),
            "message", testOrders.size() + "건 테스트 주문 삭제 완료"
        ));
    }

    // ─── 채널 조회 헬퍼 ──────────────────────────────────────────
    private SalesChannel findChannel(String channelName) {
        return salesChannelRepository.findAll().stream()
            .filter(c -> c.getChannelName() != null &&
                         c.getChannelName().contains(channelName))
            .findFirst()
            .orElse(null);
    }
}
