package com.oms.collector.repository;

import com.oms.collector.entity.Return;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReturnRepository extends JpaRepository<Return, UUID> {

    // 상태별 조회
    List<Return> findByStatusOrderByCreatedAtDesc(Return.ReturnStatus status);

    // 전체 최신순
    List<Return> findAllByOrderByCreatedAtDesc();

    // 주문번호로 조회
    List<Return> findByOrderNoOrderByCreatedAtDesc(String orderNo);

    // 판매처별 조회
    List<Return> findByChannelNameOrderByCreatedAtDesc(String channelName);

    // 날짜 범위 조회
    @Query("SELECT r FROM Return r WHERE r.createdAt BETWEEN :start AND :end ORDER BY r.createdAt DESC")
    List<Return> findByDateRange(
        @Param("start") LocalDateTime start,
        @Param("end")   LocalDateTime end
    );

    // 키워드 검색 (주문번호, 수령자, 상품명)
    @Query("SELECT r FROM Return r WHERE " +
           "r.orderNo LIKE %:kw% OR r.recipientName LIKE %:kw% OR r.productName LIKE %:kw% " +
           "ORDER BY r.createdAt DESC")
    List<Return> searchByKeyword(@Param("kw") String keyword);

    // 상태별 카운트
    long countByStatus(Return.ReturnStatus status);
}
