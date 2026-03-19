package com.oms.collector.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 창고 Entity
 * 
 * 입고/출고/이동에 사용할 창고 정보
 * InventoryService의 하드코딩 switch 대체
 */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(name = "warehouses")
@EntityListeners(AuditingEntityListener.class)
public class Warehouse {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "warehouse_id")
    private UUID warehouseId;

    /**
     * 창고 코드 (영문/숫자/언더스코어, 대문자)
     * 예: ANYANG, ICHEON_BOX, BUCHEON
     */
    @Column(name = "code", unique = true, nullable = false, length = 100)
    private String code;

    /**
     * 창고명 (화면 표시용)
     * 예: 본사(안양), 고백창고(이천)_BOX
     */
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /**
     * 창고 유형
     * REAL: 실제창고, TRANSIT: 이동중, RETURN: 반품창고,
     * DEFECT: 불량/폐기, SPECIAL: 특수창고, VIRTUAL: 가상재고, UNUSED: 미사용
     */
    @Column(name = "type", nullable = false, length = 20)
    @Builder.Default
    private String type = "REAL";

    /**
     * 활성 여부
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * 정렬 순서
     */
    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 999;

    /**
     * 비고
     */
    @Column(name = "description", length = 500)
    private String description;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
