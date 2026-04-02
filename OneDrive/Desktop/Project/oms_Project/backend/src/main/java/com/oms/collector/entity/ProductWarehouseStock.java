package com.oms.collector.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 창고별 상품 재고 테이블
 *
 * Product의 하드코딩된 3개 컬럼(anyang/icheon/bucheon)을 대체하여
 * 모든 창고의 재고를 동적으로 관리
 *
 * product_id + warehouse_code 유니크 조합
 */
@Entity
@Table(name = "product_warehouse_stock",
    uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "warehouse_code"}))
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductWarehouseStock {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "warehouse_code", nullable = false, length = 50)
    private String warehouseCode;

    @Column(name = "warehouse_name", length = 100)
    private String warehouseName;

    @Column(name = "stock", nullable = false)
    @Builder.Default
    private Integer stock = 0;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
