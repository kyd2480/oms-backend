package com.oms.collector.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SabangnetIntegrationDto {

    private UUID integrationId;
    private String companyCode;
    private String integrationName;
    private String sabangnetId;
    private String apiKey;
    private String maskedApiKey;
    private String apiBaseUrl;
    private String logisticsPlaceId;
    private Boolean enabled;
    private String memo;
    private LocalDateTime lastCollectedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
