package com.oms.collector.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 재고 이동 내역 Entity
 * 
 * 입고, 출고, 이동 등 모든 재고 변동 기록
 */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(name = "inventory_transactions")
@EntityListeners(AuditingEntityListener.class)
public class InventoryTransaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "transaction_id")
    private UUID transactionId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
    
    @Column(name = "transaction_type", nullable = false, length = 20)
    private String transactionType;  // IN(입고), OUT(출고), MOVE(이동), ADJUST(조정)
    
    @Column(name = "quantity", nullable = false)
    private Integer quantity;
    
    @Column(name = "before_stock")
    private Integer beforeStock;  // 변경 전 재고
    
    @Column(name = "after_stock")
    private Integer afterStock;  // 변경 후 재고
    
    @Column(name = "from_location", length = 100)
    private String fromLocation;  // 출발 위치
    
    @Column(name = "to_location", length = 100)
    private String toLocation;  // 도착 위치
    
    @Column(name = "reference_type", length = 50)
    private String referenceType;  // ORDER(주문), PURCHASE(발주), MANUAL(수동)
    
    @Column(name = "reference_id")
    private UUID referenceId;  // 관련 주문/발주 ID
    
    @Column(name = "notes", length = 500)
    private String notes;
    
    @Column(name = "created_by", length = 100)
    private String createdBy;  // 처리자
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    // Static Factory Methods
    
    public static InventoryTransaction createInbound(
            Product product, int quantity, String location, String notes) {
        return InventoryTransaction.builder()
            .product(product)
            .transactionType("IN")
            .quantity(quantity)
            .beforeStock(product.getTotalStock())
            .afterStock(product.getTotalStock() + quantity)
            .toLocation(location)
            .notes(notes)
            .build();
    }
    
    public static InventoryTransaction createOutbound(
            Product product, int quantity, UUID orderId, String notes) {
        return InventoryTransaction.builder()
            .product(product)
            .transactionType("OUT")
            .quantity(quantity)
            .beforeStock(product.getTotalStock())
            .afterStock(product.getTotalStock() - quantity)
            .fromLocation(product.getWarehouseLocation())
            .referenceType("ORDER")
            .referenceId(orderId)
            .notes(notes)
            .build();
    }
    
    public static InventoryTransaction createAdjustment(
            Product product, int quantity, String reason) {
        return InventoryTransaction.builder()
            .product(product)
            .transactionType("ADJUST")
            .quantity(quantity)
            .beforeStock(product.getTotalStock())
            .afterStock(product.getTotalStock() + quantity)
            .notes(reason)
            .build();
    }
}
