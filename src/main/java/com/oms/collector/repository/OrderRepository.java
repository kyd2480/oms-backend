package com.oms.collector.repository;

import com.oms.collector.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByOrderNo(String orderNo);

    Page<Order> findByOrderStatus(Order.OrderStatus status, Pageable pageable);

    /**
     * 상태별 전체 주문 조회 — items JOIN FETCH (N+1 방지)
     */
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items " +
           "WHERE o.orderStatus = :status ORDER BY o.orderedAt DESC")
    List<Order> findByOrderStatusWithItems(@Param("status") Order.OrderStatus status);

    // ── CS / 취소 / 배송 조회용 ────────────────────────────────────────────

    @Query("SELECT o FROM Order o WHERE o.orderStatus = :status " +
           "AND o.orderedAt BETWEEN :start AND :end ORDER BY o.orderedAt DESC")
    List<Order> findByOrderStatusAndDateRange(
        @Param("status") Order.OrderStatus status,
        @Param("start")  java.time.LocalDateTime start,
        @Param("end")    java.time.LocalDateTime end);

    @Query("SELECT o FROM Order o WHERE o.orderStatus = 'SHIPPED' " +
           "AND o.orderedAt BETWEEN :start AND :end ORDER BY o.orderedAt DESC")
    List<Order> findShippedByDateRange(
        @Param("start") java.time.LocalDateTime start,
        @Param("end")   java.time.LocalDateTime end);

    @Query("SELECT o FROM Order o WHERE o.orderStatus = 'CANCELLED' " +
           "AND o.orderedAt BETWEEN :start AND :end ORDER BY o.orderedAt DESC")
    List<Order> findCancelledByDateRange(
        @Param("start") java.time.LocalDateTime start,
        @Param("end")   java.time.LocalDateTime end);

    @Query("SELECT o FROM Order o WHERE o.orderedAt BETWEEN :start AND :end " +
           "AND (:keyword IS NULL OR :keyword = '' " +
           "     OR o.orderNo LIKE %:keyword% " +
           "     OR o.recipientName LIKE %:keyword% " +
           "     OR o.customerName LIKE %:keyword%) " +
           "ORDER BY o.orderedAt DESC")
    List<Order> findByDateRangeAndTracking(
        @Param("start")   java.time.LocalDateTime start,
        @Param("end")     java.time.LocalDateTime end,
        @Param("keyword") String keyword);

    @Query("SELECT o FROM Order o WHERE o.orderedAt BETWEEN :start AND :end " +
           "ORDER BY o.orderedAt DESC")
    List<Order> findByDateRange(
        @Param("start") java.time.LocalDateTime start,
        @Param("end")   java.time.LocalDateTime end);

    @Query("SELECT o FROM Order o WHERE o.orderStatus = :status " +
           "AND o.updatedAt BETWEEN :start AND :end ORDER BY o.updatedAt DESC")
    List<Order> findByOrderStatusAndUpdatedAtRange(
        @Param("status") Order.OrderStatus status,
        @Param("start")  java.time.LocalDateTime start,
        @Param("end")    java.time.LocalDateTime end);

    @Query("SELECT COUNT(o) FROM Order o WHERE DATE(o.orderedAt) = CURRENT_DATE")
    long countTodayOrders();

    @Query("SELECT o.orderNo FROM Order o WHERE DATE(o.orderedAt) = :date " +
           "ORDER BY o.orderNo DESC LIMIT 1")
    Optional<String> findLastOrderNoByDate(@Param("date") java.time.LocalDate date);

    // ────────────────────────────────────────────────────────────────────────
    // 재고 매칭용 네이티브 쿼리
    // orders + order_items + products 를 DB에서 직접 JOIN
    // Java 루프 없이 매칭 결과를 한 번에 가져와 9551건 루프 문제 해결
    // ────────────────────────────────────────────────────────────────────────

    /**
     * PENDING 주문 중 productCode로 SKU 매칭된 아이템 조회 (DB JOIN)
     */
    @Query(value = """
        SELECT
            o.order_no          AS orderNo,
            COALESCE(sc.channel_name, '') AS channelName,
            o.recipient_name    AS recipientName,
            o.address           AS address,
            CAST(o.ordered_at AS VARCHAR) AS orderedAt,
            oi.product_name     AS productName,
            oi.product_code     AS productCode,
            oi.quantity         AS quantity,
            p.product_id        AS productId,
            p.sku               AS sku,
            COALESCE(p.warehouse_stock_anyang,  0) AS warehouseStockAnyang,
            COALESCE(p.warehouse_stock_icheon,  0) AS warehouseStockIcheon,
            COALESCE(p.warehouse_stock_bucheon, 0) AS warehouseStockBucheon
        FROM orders o
        JOIN order_items oi ON oi.order_id = o.order_id
        JOIN products p     ON LOWER(p.sku) = LOWER(oi.product_code)
                            OR LOWER(p.barcode) = LOWER(oi.product_code)
        LEFT JOIN sales_channels sc ON sc.channel_id = o.channel_id
        WHERE o.order_status = 'PENDING'
          AND oi.product_code IS NOT NULL
          AND oi.product_code <> ''
          AND oi.product_code !~* '^(11ST|NAVER|CP|GS|COUPANG|KAKAO)-'
        ORDER BY o.ordered_at DESC
        """, nativeQuery = true)
    List<MatchedItemProjection> findPendingMatchedByCode();

    /**
     * PENDING 주문 중 productCode로 매칭 안 된 아이템만 조회 (상품명 매칭 대상)
     * LEFT JOIN 후 product_id IS NULL인 것만 반환
     */
    @Query(value = """
        SELECT
            o.order_no          AS orderNo,
            COALESCE(sc.channel_name, '') AS channelName,
            o.recipient_name    AS recipientName,
            o.address           AS address,
            CAST(o.ordered_at AS VARCHAR) AS orderedAt,
            oi.product_name     AS productName,
            oi.product_code     AS productCode,
            oi.quantity         AS quantity,
            NULL::uuid          AS productId,
            NULL                AS sku,
            0                   AS warehouseStockAnyang,
            0                   AS warehouseStockIcheon,
            0                   AS warehouseStockBucheon
        FROM orders o
        JOIN order_items oi ON oi.order_id = o.order_id
        LEFT JOIN products p ON LOWER(p.sku)     = LOWER(oi.product_code)
                             OR LOWER(p.barcode) = LOWER(oi.product_code)
        LEFT JOIN sales_channels sc ON sc.channel_id = o.channel_id
        WHERE o.order_status = 'PENDING'
          AND p.product_id IS NULL
        ORDER BY o.ordered_at DESC
        """, nativeQuery = true)
    List<MatchedItemProjection> findPendingUnmatchedByCode();

    /**
     * CONFIRMED 주문 아이템 조회 (allocated API용)
     */
    @Query(value = """
        SELECT
            o.order_no          AS orderNo,
            COALESCE(sc.channel_name, '') AS channelName,
            o.recipient_name    AS recipientName,
            o.address           AS address,
            CAST(o.ordered_at AS VARCHAR) AS orderedAt,
            oi.product_name     AS productName,
            oi.product_code     AS productCode,
            oi.quantity         AS quantity,
            p.product_id        AS productId,
            p.sku               AS sku,
            COALESCE(p.warehouse_stock_anyang,  0) AS warehouseStockAnyang,
            COALESCE(p.warehouse_stock_icheon,  0) AS warehouseStockIcheon,
            COALESCE(p.warehouse_stock_bucheon, 0) AS warehouseStockBucheon
        FROM orders o
        JOIN order_items oi ON oi.order_id = o.order_id
        LEFT JOIN products p ON LOWER(p.sku)     = LOWER(oi.product_code)
                             OR LOWER(p.barcode) = LOWER(oi.product_code)
        LEFT JOIN sales_channels sc ON sc.channel_id = o.channel_id
        WHERE o.order_status = 'CONFIRMED'
        ORDER BY o.ordered_at DESC
        """, nativeQuery = true)
    List<MatchedItemProjection> findConfirmedWithProducts();
}
