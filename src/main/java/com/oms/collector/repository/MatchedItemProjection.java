package com.oms.collector.repository;

import java.util.UUID;

/**
 * 재고 매칭 SQL JOIN 결과 Projection
 *
 * orders + order_items + products 를 DB에서 JOIN해서
 * Java 루프 없이 매칭 결과를 한 번에 가져옴
 */
public interface MatchedItemProjection {

    // Order
    String getOrderNo();
    String getChannelName();
    String getRecipientName();
    String getAddress();
    String getOrderedAt();

    // OrderItem
    String getProductName();
    String getOptionName();
    String getProductCode();
    Integer getQuantity();

    // Product (매칭 성공 시)
    UUID getProductId();
    String getSku();

    // 창고별 재고 (Product 컬럼)
    Integer getWarehouseStockAnyang();
    Integer getWarehouseStockIcheon();
    Integer getWarehouseStockBucheon();
}
