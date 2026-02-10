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
import java.util.UUID;

/**
 * 네이버 Mock 주문 수집기
 * 
 * 테스트용 Mock 데이터를 생성합니다.
 * 실제 API 연동 시 NaverRealCollector로 교체하면 됩니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockNaverCollector implements OrderCollector {
    
    private static final String CHANNEL_CODE = "NAVER";
    private final SalesChannelRepository salesChannelRepository;
    private final Random random = new Random();
    
    @Override
    public String getChannelCode() {
        return CHANNEL_CODE;
    }
    
    @Override
    public List<CollectedOrder> collectOrders(LocalDateTime startDate, LocalDateTime endDate) {
        log.info("🔹 [Mock] 네이버 주문 수집 시작: {} ~ {}", startDate, endDate);
        
        try {
            // Mock 데이터 생성 (2~5개)
            int orderCount = 2 + random.nextInt(4);
            List<CollectedOrder> orders = new ArrayList<>();
            
            SalesChannel channel = salesChannelRepository.findByChannelCode(CHANNEL_CODE)
                .orElseThrow(() -> new RuntimeException("네이버 판매처를 찾을 수 없습니다"));
            
            for (int i = 0; i < orderCount; i++) {
                CollectedOrder order = generateMockOrder(channel);
                orders.add(order);
                log.debug("  - Mock 주문 생성: {}", order.getChannelOrderNo());
            }
            
            log.info("✅ [Mock] 네이버 주문 {} 건 수집 완료", orders.size());
            return orders;
            
        } catch (Exception e) {
            log.error("❌ [Mock] 네이버 주문 수집 실패", e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public CollectedOrder getOrder(String channelOrderNo) {
        log.info("🔹 [Mock] 네이버 단일 주문 조회: {}", channelOrderNo);
        
        SalesChannel channel = salesChannelRepository.findByChannelCode(CHANNEL_CODE)
            .orElseThrow(() -> new RuntimeException("네이버 판매처를 찾을 수 없습니다"));
        
        return generateMockOrder(channel);
    }
    
    @Override
    public boolean testConnection() {
        log.info("🔹 [Mock] 네이버 연결 테스트");
        return true;
    }
    
    @Override
    public String getCollectorType() {
        return "MOCK";
    }
    
    /**
     * Mock 주문 생성
     */
    private CollectedOrder generateMockOrder(SalesChannel channel) {
        String orderNo = "NAVER-" + System.currentTimeMillis() + "-" + random.nextInt(1000);
        
        // 주문 기본 정보
        CollectedOrder order = CollectedOrder.builder()
            .channelId(channel.getChannelId())
            .channelCode(CHANNEL_CODE)
            .channelOrderNo(orderNo)
            .customerName(generateRandomName())
            .customerPhone(generateRandomPhone())
            .customerEmail("customer" + random.nextInt(1000) + "@test.com")
            .recipientName(generateRandomName())
            .recipientPhone(generateRandomPhone())
            .postalCode(String.format("%05d", random.nextInt(100000)))
            .address(generateRandomAddress())
            .addressDetail(random.nextInt(10) + "동 " + random.nextInt(1000) + "호")
            .deliveryMemo("문 앞에 놓아주세요")
            .status("PAYED")
            .paymentStatus("PAID")
            .paymentMethod("CARD")
            .orderedAt(LocalDateTime.now().minusHours(random.nextInt(24)))
            .paidAt(LocalDateTime.now().minusHours(random.nextInt(24)))
            .build();
        
        // 주문 상품 (1~3개)
        int itemCount = 1 + random.nextInt(3);
        BigDecimal totalAmount = BigDecimal.ZERO;
        
        for (int i = 0; i < itemCount; i++) {
            CollectedOrderItem item = generateMockItem();
            order.addItem(item);
            totalAmount = totalAmount.add(item.getTotalPrice());
        }
        
        // 금액 계산
        BigDecimal shippingFee = new BigDecimal("3000");
        BigDecimal discountAmount = new BigDecimal(random.nextInt(5000));
        
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
            "젝시믹스 레깅스", "젝시믹스 브라탑", "젝시믹스 크롭티", 
            "젝시믹스 후디", "젝시믹스 트레이닝복", "젝시믹스 요가매트"
        };
        String[] sizes = {"S", "M", "L", "XL"};
        String[] colors = {"블랙", "네이비", "차콜", "핑크", "베이지"};
        
        String productName = products[random.nextInt(products.length)];
        int quantity = 1 + random.nextInt(3);
        BigDecimal unitPrice = new BigDecimal(29900 + random.nextInt(40000));
        
        CollectedOrderItem item = CollectedOrderItem.builder()
            .channelProductCode("NAVER-PRD-" + random.nextInt(10000))
            .productName(productName)
            .optionName(sizes[random.nextInt(sizes.length)] + " / " + colors[random.nextInt(colors.length)])
            .quantity(quantity)
            .unitPrice(unitPrice)
            .barcode("880" + String.format("%010d", random.nextInt(1000000000)))
            .sku("XEXYMIX-" + random.nextInt(10000))
            .build();
        
        item.calculateTotalPrice();
        
        return item;
    }
    
    /**
     * 랜덤 이름 생성
     */
    private String generateRandomName() {
        String[] lastNames = {"김", "이", "박", "최", "정", "강", "조", "윤", "장", "임"};
        String[] firstNames = {"민수", "지혜", "서연", "준호", "유진", "하은", "도윤", "서준", "지우", "예은"};
        return lastNames[random.nextInt(lastNames.length)] + firstNames[random.nextInt(firstNames.length)];
    }
    
    /**
     * 랜덤 전화번호 생성
     */
    private String generateRandomPhone() {
        return String.format("010-%04d-%04d", random.nextInt(10000), random.nextInt(10000));
    }
    
    /**
     * 랜덤 주소 생성
     */
    private String generateRandomAddress() {
        String[] cities = {"서울특별시", "경기도", "인천광역시", "부산광역시", "대구광역시"};
        String[] districts = {"강남구", "서초구", "송파구", "마포구", "용산구", "성동구"};
        String[] roads = {"테헤란로", "강남대로", "논현로", "선릉로", "역삼로", "언주로"};
        
        return String.format("%s %s %s %d", 
            cities[random.nextInt(cities.length)],
            districts[random.nextInt(districts.length)],
            roads[random.nextInt(roads.length)],
            random.nextInt(500) + 1
        );
    }
}
