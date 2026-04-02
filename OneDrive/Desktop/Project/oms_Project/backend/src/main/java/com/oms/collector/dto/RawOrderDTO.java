package com.oms.collector.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * RawOrder DTO
 * 
 * Entity를 직접 반환하지 않고 DTO로 변환하여 반환
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RawOrderDTO {
    
    private UUID rawOrderId;
    private UUID channelId;
    private String channelCode;
    private String channelName;
    private String channelOrderNo;
    private String rawData;
    private LocalDateTime collectedAt;
    private Boolean processed;
    private LocalDateTime processedAt;
    private String errorMessage;
    private LocalDateTime createdAt;
}
