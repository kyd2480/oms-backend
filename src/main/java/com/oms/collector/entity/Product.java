package com.oms.collector.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 상품 Entity
 * 
 * 판매 상품 정보 및 기본 재고 관리
 */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(name = "products")
@EntityListeners(AuditingEntityListener.class)
public class Product {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "product_id")
    private UUID productId;
    
    @Column(name = "sku", unique = true, nullable = false, length = 100)
    private String sku;  // Stock Keeping Unit
    
    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;
    
    @Column(name = "barcode", length = 100)
    private String barcode;
    
    @Column(name = "category", length = 100)
    private String category;
    
    @Column(name = "cost_price", precision = 10, scale = 2)
    private BigDecimal costPrice;  // 원가
    
    @Column(name = "selling_price", precision = 10, scale = 2)
    private BigDecimal sellingPrice;  // 판매가
    
    @Column(name = "total_stock")
    private Integer totalStock = 0;  // 총 재고
    
    @Column(name = "available_stock")
    private Integer availableStock = 0;  // 가용 재고
    
    @Column(name = "reserved_stock")
    private Integer reservedStock = 0;  // 예약 재고 (주문 대기)
    
    @Column(name = "safety_stock")
    private Integer safetyStock = 0;  // 안전 재고
    
    @Column(name = "warehouse_location", length = 100)
    private String warehouseLocation;  // 창고 위치
    
    @Column(name = "is_active")
    private Boolean isActive = true;
    
    @Column(name = "description", length = 500)
    private String description;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Business Methods
    
    /**
     * 재고 추가 (입고)
     */
    public void increaseStock(int quantity) {
        this.totalStock += quantity;
        this.availableStock += quantity;
    }
    
    /**
     * 재고 차감 (출고)
     */
    public void decreaseStock(int quantity) {
        if (this.availableStock < quantity) {
            throw new IllegalStateException("재고가 부족합니다.");
        }
        this.totalStock -= quantity;
        this.availableStock -= quantity;
    }
    
    /**
     * 재고 예약
     */
    public void reserveStock(int quantity) {
        if (this.availableStock < quantity) {
            throw new IllegalStateException("가용 재고가 부족합니다.");
        }
        this.availableStock -= quantity;
        this.reservedStock += quantity;
    }
    
    /**
     * 재고 예약 취소
     */
    public void releaseReservedStock(int quantity) {
        if (this.reservedStock < quantity) {
            throw new IllegalStateException("예약 재고가 부족합니다.");
        }
        this.reservedStock -= quantity;
        this.availableStock += quantity;
    }
    
    /**
     * 예약 재고 확정 (출고 완료)
     */
    public void confirmReservedStock(int quantity) {
        if (this.reservedStock < quantity) {
            throw new IllegalStateException("예약 재고가 부족합니다.");
        }
        this.totalStock -= quantity;
        this.reservedStock -= quantity;
    }
    
    /**
     * 안전 재고 미달 여부
     */
    public boolean isBelowSafetyStock() {
        return this.availableStock <= this.safetyStock;
    }
    
    /**
     * 재고 없음 여부
     */
    public boolean isOutOfStock() {
        return this.availableStock == 0;
    }
}
