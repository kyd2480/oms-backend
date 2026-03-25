package com.oms.collector.repository;

import com.oms.collector.entity.Order;
import com.oms.collector.entity.SalesChannel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 주문 Repository
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    
    /**
     * 주문번호로 조회
     */
    Optional<Order> findByOrderNo(String orderNo);
    
    /**
     * 판매처 주문번호로 조회
     */
    Optional<Order> findByChannelAndChannelOrderNo(SalesChannel channel, String channelOrderNo);
    
    /**
     * 판매처별 주문 조회
     */
    List<Order> findByChannelOrderByOrderedAtDesc(SalesChannel channel);
    
    /**
     * 상태별 주문 조회
     */
    List<Order> findByOrderStatusOrderByOrderedAtDesc(Order.OrderStatus status);

    /**
     * 상태별 주문 조회 (페이지네이션)
     */
    Page<Order> findByOrderStatus(Order.OrderStatus status, Pageable pageable);
    
    /**
     * 기간별 주문 조회
     */
    @Query("SELECT o FROM Order o WHERE o.orderedAt BETWEEN :startDate AND :endDate " +
           "ORDER BY o.orderedAt DESC")
    List<Order> findByDateRange(@Param("startDate") LocalDateTime startDate,
                                @Param("endDate") LocalDateTime endDate);
    
    /**
     * 판매처 + 기간별 주문 조회
     */
    @Query("SELECT o FROM Order o WHERE o.channel = :channel " +
           "AND o.orderedAt BETWEEN :startDate AND :endDate " +
           "ORDER BY o.orderedAt DESC")
    List<Order> findByChannelAndDateRange(
        @Param("channel") SalesChannel channel,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    /**
     * 고객 전화번호로 주문 조회
     */
    List<Order> findByCustomerPhoneOrderByOrderedAtDesc(String customerPhone);
    
    /**
     * 오늘 주문 수 조회
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE CAST(o.orderedAt AS LocalDate) = CURRENT_DATE")
    long countTodayOrders();
    
    /**
     * 판매처별 오늘 주문 수
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.channel = :channel " +
           "AND CAST(o.orderedAt AS LocalDate) = CURRENT_DATE")
    long countTodayOrdersByChannel(@Param("channel") SalesChannel channel);
    
    /**
     * 최근 N일 주문 통계
     */
    @Query("SELECT CAST(o.orderedAt AS LocalDate) as date, COUNT(o) as count " +
           "FROM Order o WHERE o.orderedAt >= :since " +
           "GROUP BY CAST(o.orderedAt AS LocalDate) ORDER BY CAST(o.orderedAt AS LocalDate) DESC")
    List<Object[]> getOrderStatisticsSince(@Param("since") LocalDateTime since);
    
    /**
     * 상태 + 주문일자 기간 조회 (InvoiceController 사용)
     */
    @Query("SELECT o FROM Order o WHERE o.orderStatus = :status " +
           "AND o.orderedAt BETWEEN :startDate AND :endDate " +
           "ORDER BY o.orderedAt DESC")
    List<Order> findByOrderStatusAndDateRange(
        @Param("status")    Order.OrderStatus status,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate")   LocalDateTime endDate
    );

    /**
     * 상태 + updatedAt 기간 조회 (InvoiceController - 발송 완료 조회 사용)
     */
    @Query("SELECT o FROM Order o WHERE o.orderStatus = :status " +
           "AND o.updatedAt BETWEEN :startDate AND :endDate " +
           "ORDER BY o.updatedAt DESC")
    List<Order> findByOrderStatusAndUpdatedAtRange(
        @Param("status")    Order.OrderStatus status,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate")   LocalDateTime endDate
    );

    /**
     * 취소 주문 기간 조회 (updatedAt 기준 — 취소 처리 시점)
     */
    @Query("SELECT o FROM Order o WHERE o.orderStatus = 'CANCELLED' " +
           "AND o.updatedAt BETWEEN :startDate AND :endDate " +
           "ORDER BY o.updatedAt DESC")
    List<Order> findCancelledByDateRange(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate")   LocalDateTime endDate
    );

    /**
     * CS 검색 — 발송일자(updatedAt) 기준 기간 조회
     */
    @Query("SELECT o FROM Order o WHERE o.updatedAt BETWEEN :startDate AND :endDate " +
           "AND o.orderStatus = 'SHIPPED' " +
           "ORDER BY o.updatedAt DESC")
    List<Order> findShippedByDateRange(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate")   LocalDateTime endDate
    );

    /**
     * CS 검색 — 기간 + deliveryMemo의 송장번호 포함 검색 (주문일자 기준)
     */
    @Query("SELECT o FROM Order o WHERE o.orderedAt BETWEEN :startDate AND :endDate " +
           "AND o.deliveryMemo LIKE %:tracking% " +
           "ORDER BY o.orderedAt DESC")
    List<Order> findByDateRangeAndTracking(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate")   LocalDateTime endDate,
        @Param("tracking")  String tracking
    );

    /**
     * 특정 날짜의 마지막 주문번호 조회 (시퀀스 생성용)
     * 
     * @param dateString YYYYMMDD 형식의 날짜 (예: "20260209")
     * @return 마지막 주문번호 (예: "OMS-20260209-0005")
     */
    @Query("SELECT o.orderNo FROM Order o WHERE o.orderNo LIKE CONCAT('OMS-', :dateString, '-%') " +
           "ORDER BY o.orderNo DESC LIMIT 1")
    String findLastOrderNoByDate(@Param("dateString") String dateString);
}
