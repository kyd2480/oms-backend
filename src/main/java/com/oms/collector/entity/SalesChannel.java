package com.oms.collector.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 판매처 Entity
 * 
 * 네이버, 쿠팡, 11번가 등 주문을 수집할 판매처 정보
 */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(name = "sales_channels")
@EntityListeners(AuditingEntityListener.class)
public class SalesChannel {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "channel_id")
    private UUID channelId;
    
    @Column(name = "channel_code", unique = true, nullable = false, length = 50)
    private String channelCode;  // NAVER, COUPANG, 11ST
    
    @Column(name = "channel_name", nullable = false, length = 100)
    private String channelName;  // 네이버 스마트스토어
    
    @Column(name = "api_type", length = 20)
    private String apiType;  // REST, SOAP, CSV
    
    @Column(name = "api_base_url")
    private String apiBaseUrl;
    
    @Column(name = "credentials", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String credentials;  // JSON 형식의 인증 정보
    
    @Column(name = "is_active")
    private Boolean isActive = true;
    
    @Column(name = "collection_interval")
    private Integer collectionInterval = 10;  // 수집 주기 (분)
    
    @Column(name = "last_collected_at")
    private LocalDateTime lastCollectedAt;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Business Methods
    public void updateLastCollectedTime() {
        this.lastCollectedAt = LocalDateTime.now();
    }
    
    public boolean isApiType() {
        return "REST".equalsIgnoreCase(this.apiType) || "SOAP".equalsIgnoreCase(this.apiType);
    }
    
    public boolean isCsvType() {
        return "CSV".equalsIgnoreCase(this.apiType);
    }
}
