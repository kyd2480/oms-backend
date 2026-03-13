package com.oms.collector.repository;

import com.oms.collector.entity.BundleGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BundleGroupRepository extends JpaRepository<BundleGroup, UUID> {

    // 묶음 키로 조회
    Optional<BundleGroup> findByBundleKey(String bundleKey);

    // 상태별 조회
    List<BundleGroup> findByStatusOrderByCreatedAtDesc(BundleGroup.BundleStatus status);

    // 전체 조회 (최신순)
    List<BundleGroup> findAllByOrderByCreatedAtDesc();

    // 특정 주문번호가 포함된 묶음 조회
    List<BundleGroup> findByOrderNosContaining(String orderNo);
}
