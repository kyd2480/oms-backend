package com.oms.collector.repository;

import com.oms.collector.entity.RawOrder;
import com.oms.collector.entity.SalesChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 원본 주문 Repository
 */
@Repository
public interface RawOrderRepository extends JpaRepository<RawOrder, UUID> {
    
    /**
     * 판매처 + 주문번호로 조회
     */
    Optional<RawOrder> findByChannelAndChannelOrderNo(SalesChannel channel, String channelOrderNo);
    
    /**
     * 미처리 주문 조회
     */
    List<RawOrder> findByProcessedFalseOrderByCollectedAtAsc();
    
    /**
     * 판매처별 미처리 주문 조회
     */
    List<RawOrder> findByChannelAndProcessedFalseOrderByCollectedAtAsc(SalesChannel channel);
    
    /**
     * 기간별 수집된 주문 조회
     */
    @Query("SELECT r FROM RawOrder r WHERE r.channel = :channel " +
           "AND r.collectedAt BETWEEN :startDate AND :endDate " +
           "ORDER BY r.collectedAt DESC")
    List<RawOrder> findByChannelAndDateRange(
        @Param("channel") SalesChannel channel,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    /**
     * 판매처 + 주문번호 존재 여부 확인
     */
    boolean existsByChannelAndChannelOrderNo(SalesChannel channel, String channelOrderNo);
    
    /**
     * 에러 발생 주문 조회
     */
    List<RawOrder> findByProcessedFalseAndErrorMessageIsNotNullOrderByCollectedAtDesc();
    
    /**
     * 판매처별 수집 통계
     */
    @Query("SELECT COUNT(r) FROM RawOrder r WHERE r.channel = :channel " +
           "AND r.collectedAt >= :since")
    long countByChannelSince(@Param("channel") SalesChannel channel, 
                             @Param("since") LocalDateTime since);
}
