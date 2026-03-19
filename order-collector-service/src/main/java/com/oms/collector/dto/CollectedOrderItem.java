package com.oms.collector.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 수집된 주문 상품 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectedOrderItem {
    
    // 상품 정보
    private String channelProductCode;  // 판매처 상품코드
    private String productName;
    private String optionName;          // 옵션 (사이즈, 색상 등)
    
    // 수량 및 가격
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    
    // 추가 정보
    private String barcode;
    private String sku;
    
    // 비즈니스 메서드
    public void calculateTotalPrice() {
        if (this.quantity != null && this.unitPrice != null) {
            this.totalPrice = this.unitPrice.multiply(BigDecimal.valueOf(this.quantity));
        }
    }
    
    public boolean isValid() {
        return productName != null 
            && quantity != null 
            && quantity > 0
            && unitPrice != null
            && unitPrice.compareTo(BigDecimal.ZERO) > 0;
    }
}
