package com.oms.collector.collector.impl;

import com.oms.collector.collector.OrderCollector;
import com.oms.collector.dto.CollectedOrder;
import com.oms.collector.dto.CollectedOrderItem;
import com.oms.collector.entity.SalesChannel;
import com.oms.collector.repository.SalesChannelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 쿠팡 Mock 주문 수집기
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockCoupangCollector implements OrderCollector {
    
    private static final String CHANNEL_CODE = "COUPANG";
    private final SalesChannelRepository salesChannelRepository;
    private final Random random = new Random();
    
    @Override
    public String getChannelCode() {
        return CHANNEL_CODE;
    }
    
    @Override
    public List<CollectedOrder> collectOrders(LocalDateTime startDate, LocalDateTime endDate) {
        log.info("🔹 [Mock] 쿠팡 주문 수집 시작: {} ~ {}", startDate, endDate);
        
        try {
            int orderCount = 1 + random.nextInt(3);
            List<CollectedOrder> orders = new ArrayList<>();
            
            SalesChannel channel = salesChannelRepository.findByChannelCode(CHANNEL_CODE)
                .orElseThrow(() -> new RuntimeException("쿠팡 판매처를 찾을 수 없습니다"));
            
            for (int i = 0; i < orderCount; i++) {
                CollectedOrder order = generateMockOrder(channel);
                orders.add(order);
            }
            
            log.info("✅ [Mock] 쿠팡 주문 {} 건 수집 완료", orders.size());
            return orders;
            
        } catch (Exception e) {
            log.error("❌ [Mock] 쿠팡 주문 수집 실패", e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public CollectedOrder getOrder(String channelOrderNo) {
        log.info("🔹 [Mock] 쿠팡 단일 주문 조회: {}", channelOrderNo);
        
        SalesChannel channel = salesChannelRepository.findByChannelCode(CHANNEL_CODE)
            .orElseThrow(() -> new RuntimeException("쿠팡 판매처를 찾을 수 없습니다"));
        
        return generateMockOrder(channel);
    }
    
    @Override
    public boolean testConnection() {
        log.info("🔹 [Mock] 쿠팡 연결 테스트");
        return true;
    }
    
    private CollectedOrder generateMockOrder(SalesChannel channel) {
        String orderNo = "CP-" + System.currentTimeMillis() + "-" + random.nextInt(1000);
        
        CollectedOrder order = CollectedOrder.builder()
            .channelId(channel.getChannelId())
            .channelCode(CHANNEL_CODE)
            .channelOrderNo(orderNo)
            .customerName("쿠팡고객" + random.nextInt(100))
            .customerPhone(String.format("010-%04d-%04d", random.nextInt(10000), random.nextInt(10000)))
            .customerEmail("coupang" + random.nextInt(1000) + "@test.com")
            .recipientName("쿠팡수령인" + random.nextInt(100))
            .recipientPhone(String.format("010-%04d-%04d", random.nextInt(10000), random.nextInt(10000)))
            .postalCode(String.format("%05d", random.nextInt(100000)))
            .address("경기도 성남시 분당구 쿠팡로 " + (random.nextInt(500) + 1))
            .addressDetail(random.nextInt(10) + "동 " + random.nextInt(1000) + "호")
            .deliveryMemo("로켓배송 부탁드립니다")
            .status("PAYED")
            .paymentStatus("PAID")
            .paymentMethod("CARD")
            .orderedAt(LocalDateTime.now().minusHours(random.nextInt(24)))
            .paidAt(LocalDateTime.now().minusHours(random.nextInt(24)))
            .build();
        
        // 주문 상품
        CollectedOrderItem item = CollectedOrderItem.builder()
            .channelProductCode("CP-PRD-" + random.nextInt(10000))
            .productName("젝시믹스 " + (random.nextBoolean() ? "레깅스" : "상의"))
            .optionName("M / 블랙")
            .quantity(1 + random.nextInt(2))
            .unitPrice(new BigDecimal(35000 + random.nextInt(30000)))
            .build();
        
        item.calculateTotalPrice();
        order.addItem(item);
        
        // 금액 계산
        order.setTotalAmount(item.getTotalPrice());
        order.setShippingFee(BigDecimal.ZERO); // 쿠팡은 무료배송
        order.setDiscountAmount(new BigDecimal(random.nextInt(3000)));
        order.setPaymentAmount(order.getTotalAmount().subtract(order.getDiscountAmount()));
        
        // 원본 JSON은 null로 설정 → RawOrderService가 전체 객체를 JSON으로 변환
        order.setRawJson(null);
        
        return order;
    }
}
