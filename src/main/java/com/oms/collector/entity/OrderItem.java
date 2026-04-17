package com.oms.collector.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 주문 상품 Entity
 * 
 * 주문에 포함된 개별 상품 정보
 */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(name = "order_items")
@EntityListeners(AuditingEntityListener.class)
public class OrderItem {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "item_id")
    private UUID itemId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore  // 순환 참조 방지
    private Order order;
    
    // 상품 정보
    @Column(name = "product_code", length = 100)
    private String productCode;  // 자사 상품 코드
    
    @Column(name = "channel_product_code", length = 100)
    private String channelProductCode;  // 판매처 상품 코드
    
    @Column(name = "product_name", nullable = false)
    private String productName;
    
    @Column(name = "option_name")
    private String optionName;  // 사이즈, 색상 등
    
    // 수량 및 가격
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "cancelled_quantity", nullable = false)
    @Builder.Default
    private Integer cancelledQuantity = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_status", length = 20, nullable = false)
    @Builder.Default
    private ItemStatus itemStatus = ItemStatus.ACTIVE;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;
    
    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;
    
    @Column(name = "total_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalPrice;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    // Business Methods
    public void calculateTotalPrice() {
        if (this.quantity != null && this.unitPrice != null) {
            this.totalPrice = this.unitPrice.multiply(BigDecimal.valueOf(this.quantity));
        }
    }
    
    public boolean hasProductCode() {
        return this.productCode != null && !this.productCode.isBlank();
    }

    public int getActiveQuantity() {
        int ordered = this.quantity != null ? this.quantity : 0;
        int cancelled = this.cancelledQuantity != null ? this.cancelledQuantity : 0;
        return Math.max(0, ordered - cancelled);
    }

    public void applyCancelledQuantity(int quantity, String reason) {
        int ordered = this.quantity != null ? this.quantity : 0;
        int nextCancelled = Math.min(ordered, Math.max(0, (this.cancelledQuantity != null ? this.cancelledQuantity : 0) + quantity));
        this.cancelledQuantity = nextCancelled;
        this.cancelReason = reason;
        if (nextCancelled <= 0) {
            this.itemStatus = ItemStatus.ACTIVE;
        } else if (nextCancelled >= ordered) {
            this.itemStatus = ItemStatus.CANCELLED;
        } else {
            this.itemStatus = ItemStatus.PARTIAL_CANCELLED;
        }
    }

    public enum ItemStatus {
        ACTIVE,
        PARTIAL_CANCELLED,
        CANCELLED
    }
}
