package com.oms.collector.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Builder.Default;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(name = "sabangnet_integrations")
@EntityListeners(AuditingEntityListener.class)
public class SabangnetIntegration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "integration_id")
    private UUID integrationId;

    @Column(name = "company_code", nullable = false, length = 20)
    private String companyCode;

    @Column(name = "integration_name", nullable = false, length = 100)
    private String integrationName;

    @Column(name = "sabangnet_id", nullable = false, length = 100)
    private String sabangnetId;

    @Column(name = "api_key", nullable = false, length = 500)
    private String apiKey;

    @Column(name = "api_base_url", nullable = false, length = 300)
    private String apiBaseUrl;

    @Column(name = "logistics_place_id", length = 100)
    private String logisticsPlaceId;

    @Column(name = "enabled", nullable = false)
    @Default
    private Boolean enabled = true;

    @Column(name = "memo", length = 500)
    private String memo;

    @Column(name = "last_collected_at")
    private LocalDateTime lastCollectedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
