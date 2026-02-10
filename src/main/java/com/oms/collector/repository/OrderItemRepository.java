package com.oms.collector.repository;

import com.oms.collector.entity.Order;
import com.oms.collector.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 주문 상품 Repository
 */
@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
    
    /**
     * 주문별 상품 조회
     */
    List<OrderItem> findByOrder(Order order);
    
    /**
     * 주문 ID로 상품 조회
     */
    List<OrderItem> findByOrderOrderId(UUID orderId);
    
    /**
     * 상품 코드로 주문 상품 조회
     */
    List<OrderItem> findByProductCode(String productCode);
    
    /**
     * 판매처 상품 코드로 조회
     */
    List<OrderItem> findByChannelProductCode(String channelProductCode);
    
    /**
     * 특정 주문의 총 상품 수
     */
    @Query("SELECT SUM(oi.quantity) FROM OrderItem oi WHERE oi.order = :order")
    Long getTotalQuantityByOrder(@Param("order") Order order);
}
