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
     * 입고 요청 (창고 지정)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InboundWarehouseRequest {
        private UUID productId;
        private Integer quantity;
        private String warehouse;  // 1.본사(안양), 2.고백창고(이천), 3.부천검수창고
        private String location;
        private String notes;
    }
    
    /**
     * 출고 요청 (창고 지정)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OutboundWarehouseRequest {
        private UUID productId;
        private Integer quantity;
        private String warehouse;  // 1.본사(안양), 2.고백창고(이천), 3.부천검수창고
        private UUID orderId;
        private String notes;
    }
    
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
     * 재고현황 목록에서 특정 창고 재고/제외 내용을 직접 보정
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockUpdateRequest {
        private String warehouseCode;
        private String warehouseName;
        private Integer warehouseStock;
        private Integer reservedStock;
        private String warehouseLocation;
        private String note;
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
        private String referenceType;
        private String notes;
        private String createdBy;
        private LocalDateTime createdAt;
    }

    /**
     * 재고현황 상품 상세의 미발송 주문 내역
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnshippedOrderItemResponse {
        private String orderNo;
        private String orderedAt;
        private String salesChannel;
        private String channelOrderNo;
        private String productName;
        private String optionName;
        private String productCode;
        private String recipientName;
        private String customerName;
        private String invoiceNo;
        private String shipAvailability;
        private Integer quantity;
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
