package com.oms.collector.repository;

import com.oms.collector.entity.SalesChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 판매처 Repository
 */
@Repository
public interface SalesChannelRepository extends JpaRepository<SalesChannel, UUID> {
    
    /**
     * 판매처 코드로 조회
     */
    Optional<SalesChannel> findByChannelCode(String channelCode);
    
    /**
     * 활성화된 판매처 목록 조회
     */
    List<SalesChannel> findByIsActiveTrue();
    
    /**
     * API 타입별 판매처 조회
     */
    List<SalesChannel> findByApiTypeAndIsActiveTrue(String apiType);
    
    /**
     * 판매처 코드 존재 여부 확인
     */
    boolean existsByChannelCode(String channelCode);
}
