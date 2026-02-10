package com.oms.collector.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 수집된 주문 DTO
 * 
 * 판매처에서 수집한 주문 데이터를 담는 객체
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectedOrder {
    
    // 판매처 정보
    private UUID channelId;
    private String channelCode;
    private String channelOrderNo;  // 판매처 주문번호
    
    // 고객 정보
    private String customerName;
    private String customerPhone;
    private String customerEmail;
    
    // 배송 정보
    private String recipientName;
    private String recipientPhone;
    private String postalCode;
    private String address;
    private String addressDetail;
    private String deliveryMemo;
    
    // 금액 정보
    private BigDecimal totalAmount;
    private BigDecimal paymentAmount;
    private BigDecimal shippingFee;
    private BigDecimal discountAmount;
    
    // 상태 정보
    private String status;          // 주문 상태
    private String paymentStatus;   // 결제 상태
    private String paymentMethod;   // 결제 수단
    
    // 날짜 정보
    private LocalDateTime orderedAt;
    private LocalDateTime paidAt;
    
    // 주문 상품 목록
    @Builder.Default
    private List<CollectedOrderItem> items = new ArrayList<>();
    
    // 원본 JSON (디버깅용)
    private String rawJson;
    
    // 비즈니스 메서드
    public void addItem(CollectedOrderItem item) {
        this.items.add(item);
    }
    
    public int getTotalQuantity() {
        return items.stream()
            .mapToInt(CollectedOrderItem::getQuantity)
            .sum();
    }
    
    public boolean isValid() {
        return channelOrderNo != null 
            && recipientName != null 
            && recipientPhone != null
            && address != null
            && !items.isEmpty();
    }
}
