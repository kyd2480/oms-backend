package com.oms.collector.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 원본 주문 Entity
 * 
 * 판매처에서 수집한 원본 JSON 데이터를 저장
 */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(name = "raw_orders", 
       uniqueConstraints = @UniqueConstraint(
           name = "uk_raw_orders_channel_order",
           columnNames = {"channel_id", "channel_order_no"}
       ))
@EntityListeners(AuditingEntityListener.class)
public class RawOrder {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "raw_order_id")
    private UUID rawOrderId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", nullable = false)
    private SalesChannel channel;
    
    @Column(name = "channel_order_no", nullable = false, length = 100)
    private String channelOrderNo;  // 판매처 주문번호
    
    @Column(name = "raw_data", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String rawData;  // 원본 JSON 데이터
    
    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;
    
    @Column(name = "processed")
    private Boolean processed = false;
    
    @Column(name = "processed_at")
    private LocalDateTime processedAt;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    // Business Methods
    public void markAsProcessed() {
        this.processed = true;
        this.processedAt = LocalDateTime.now();
    }
    
    public void markAsError(String errorMessage) {
        this.processed = false;
        this.errorMessage = errorMessage;
    }
    
    public boolean isProcessed() {
        return Boolean.TRUE.equals(this.processed);
    }
}
