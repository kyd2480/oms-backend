package com.oms.collector.collector.impl;

import com.oms.collector.collector.OrderCollector;
import com.oms.collector.dto.CollectedOrder;
import com.oms.collector.dto.CollectedOrderItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 11번가 Mock 주문 수집기
 */
@Slf4j
@Component
public class Mock11StCollector implements OrderCollector {
    
    private final Random random = new Random();
    
    @Override
    public String getChannelCode() {
        return "11ST";
    }
    
    @Override
    public String getCollectorType() {
        return "MOCK";
    }
    
    @Override
    public List<CollectedOrder> collectOrders(LocalDateTime startDate, LocalDateTime endDate) {
        log.info("🔹 [Mock] 11번가 주문 수집 시작: {} ~ {}", startDate, endDate);
        
        List<CollectedOrder> orders = new ArrayList<>();
        
        // 1~2개 주문 생성
        int orderCount = 1 + random.nextInt(2);
        
        for (int i = 0; i < orderCount; i++) {
            orders.add(generateMockOrder());
        }
        
        log.info("✅ [Mock] 11번가 주문 {} 건 수집 완료", orders.size());
        
        return orders;
    }
    
    @Override
    public CollectedOrder getOrder(String channelOrderNo) {
        return generateMockOrder();
    }
    
    @Override
    public boolean testConnection() {
        log.info("✅ [Mock] 11번가 연결 테스트 성공");
        return true;
    }
    
    /**
     * Mock 주문 생성
     */
    private CollectedOrder generateMockOrder() {
        String orderNo = "11ST-" + System.currentTimeMillis() + "-" + random.nextInt(1000);
        
        String[] names = {"김철수", "이영희", "박민수", "정지훈", "최서연", "강동원"};
        String[] addresses = {
            "서울특별시 강남구 테헤란로 152",
            "서울특별시 송파구 올림픽로 300",
            "경기도 성남시 분당구 판교역로 235",
            "인천광역시 연수구 송도과학로 32"
        };
        
        CollectedOrder order = CollectedOrder.builder()
            .channelId(null)
            .channelCode("11ST")
            .channelOrderNo(orderNo)
            .customerName(names[random.nextInt(names.length)])
            .customerPhone("010-" + (1000 + random.nextInt(9000)) + "-" + (1000 + random.nextInt(9000)))
            .customerEmail("11st" + random.nextInt(1000) + "@test.com")
            .recipientName(names[random.nextInt(names.length)])
            .recipientPhone("010-" + (1000 + random.nextInt(9000)) + "-" + (1000 + random.nextInt(9000)))
            .postalCode(String.format("%05d", random.nextInt(100000)))
            .address(addresses[random.nextInt(addresses.length)])
            .addressDetail((random.nextInt(20) + 1) + "층 " + (random.nextInt(10) + 1) + "호")
            .deliveryMemo("부재시 문앞에 놓아주세요")
            .status("PAYED")
            .paymentStatus("PAID")
            .paymentMethod("CARD")
            .orderedAt(LocalDateTime.now().minusHours(random.nextInt(24)))
            .paidAt(LocalDateTime.now().minusHours(random.nextInt(12)))
            .items(new ArrayList<>())
            .build();
        
        // 1~2개 상품
        int itemCount = 1 + random.nextInt(2);
        BigDecimal totalAmount = BigDecimal.ZERO;
        
        for (int i = 0; i < itemCount; i++) {
            CollectedOrderItem item = generateMockItem();
            order.addItem(item);
            totalAmount = totalAmount.add(item.getTotalPrice());
        }
        
        // 금액 계산
        BigDecimal shippingFee = new BigDecimal("2500");
        BigDecimal discountAmount = new BigDecimal(random.nextInt(3000));
        
        order.setTotalAmount(totalAmount);
        order.setShippingFee(shippingFee);
        order.setDiscountAmount(discountAmount);
        order.setPaymentAmount(totalAmount.add(shippingFee).subtract(discountAmount));
        
        // 원본 JSON은 null로 설정 → RawOrderService가 전체 객체를 JSON으로 변환
        order.setRawJson(null);
        
        return order;
    }
    
    /**
     * Mock 주문 상품 생성
     */
    private CollectedOrderItem generateMockItem() {
        String[] products = {
            "11번가 특가 레깅스", "스포츠 브라", "운동 반팔티", 
            "트레이닝 팬츠", "러닝화", "요가매트"
        };
        String[] sizes = {"S", "M", "L", "XL", "FREE"};
        String[] colors = {"블랙", "화이트", "그레이", "네이비"};
        
        String productName = products[random.nextInt(products.length)];
        int quantity = 1 + random.nextInt(2);
        BigDecimal unitPrice = new BigDecimal(19900 + random.nextInt(30000));
        
        CollectedOrderItem item = CollectedOrderItem.builder()
            .channelProductCode("11ST-PRD-" + random.nextInt(10000))
            .productName(productName)
            .optionName(sizes[random.nextInt(sizes.length)] + " / " + colors[random.nextInt(colors.length)])
            .quantity(quantity)
            .unitPrice(unitPrice)
            .build();
        
        item.calculateTotalPrice();
        
        return item;
    }
}
