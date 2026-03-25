package com.oms.collector.repository;

import com.oms.collector.entity.CsMemo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CsMemoRepository extends JpaRepository<CsMemo, UUID> {

    // 주문번호로 메모 조회 (시간 오름차순 - 오래된 것이 위)
    List<CsMemo> findByOrderNoOrderByCreatedAtAsc(String orderNo);
}
