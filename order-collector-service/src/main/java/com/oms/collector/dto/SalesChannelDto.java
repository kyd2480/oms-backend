package com.oms.collector.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 판매처 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesChannelDto {
    
    private UUID channelId;
    private String channelCode;
    private String channelName;
    private String apiType;
    private String apiBaseUrl;
    private String credentials;
    private Boolean isActive;
    private Integer collectionInterval;
    private LocalDateTime lastCollectedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
