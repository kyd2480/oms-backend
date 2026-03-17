package com.oms.collector.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 상품명 매칭 룰
 * 쇼핑몰 상품명 → 자사 재고 상품 매핑 저장
 * 한번 매칭되면 저장되어 이후 동일 상품명 자동 적용
 */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(name = "product_matching_rules",
    uniqueConstraints = @UniqueConstraint(columnNames = {"channel_product_name"}))
@EntityListeners(AuditingEntityListener.class)
public class ProductMatchingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "rule_id")
    private UUID ruleId;

    @Column(name = "channel_product_name", nullable = false, length = 500)
    private String channelProductName;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "sku", length = 100)
    private String sku;

    @Column(name = "match_type", length = 10)
    private String matchType;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
