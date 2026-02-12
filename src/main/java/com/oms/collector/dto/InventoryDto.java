package com.oms.collector.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 재고 관리 DTO 모음
 */
public class InventoryDto {
    
    /**
     * 입고 요청
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InboundRequest {
        private UUID productId;
        private Integer quantity;
        private String location;
        private String notes;
    }
    
    /**
     * 출고 요청
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OutboundRequest {
        private UUID productId;
        private Integer quantity;
        private UUID orderId;
        private String notes;
    }
    
    /**
     * 재고 조정 요청
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdjustRequest {
        private UUID productId;
        private Integer quantity;
        private String reason;
    }
    
    /**
     * 거래 내역 응답
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionResponse {
        private UUID transactionId;
        private UUID productId;
        private String productName;
        private String sku;
        private String transactionType;
        private Integer quantity;
        private Integer beforeStock;
        private Integer afterStock;
        private String fromLocation;
        private String toLocation;
        private String notes;
        private String createdBy;
        private LocalDateTime createdAt;
    }
    
    /**
     * 재고 통계 응답
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatsResponse {
        private Integer totalProducts;
        private Integer totalStockValue;
        private Integer lowStockCount;
        private Integer outOfStockCount;
    }
}
