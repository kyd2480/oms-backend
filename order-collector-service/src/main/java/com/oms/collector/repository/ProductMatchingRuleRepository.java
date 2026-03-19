package com.oms.collector.repository;

import com.oms.collector.entity.ProductMatchingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductMatchingRuleRepository extends JpaRepository<ProductMatchingRule, UUID> {
    Optional<ProductMatchingRule> findByChannelProductName(String channelProductName);
    List<ProductMatchingRule> findAllByOrderByCreatedAtDesc();
    boolean existsByChannelProductName(String channelProductName);
}
