package com.oms.collector.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 상품 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDto {
    
    private UUID productId;
    private String sku;
    private String productName;
    private String barcode;
    private String category;
    private BigDecimal costPrice;
    private BigDecimal sellingPrice;
    private Integer totalStock;
    private Integer availableStock;
    private Integer reservedStock;
    private Integer safetyStock;
    private String warehouseLocation;
    private Integer warehouseStockAnyang;    // 1.본사(안양) 재고
    private Integer warehouseStockIcheon;    // 2.고백창고(이천) 재고
    private Integer warehouseStockBucheon;   // 3.부천검수창고 재고
    private Boolean isActive;
    private String description;
    private Boolean isBelowSafetyStock;
    private Boolean isOutOfStock;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
