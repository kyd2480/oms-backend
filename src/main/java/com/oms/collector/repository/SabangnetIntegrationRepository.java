package com.oms.collector.repository;

import com.oms.collector.entity.SabangnetIntegration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SabangnetIntegrationRepository extends JpaRepository<SabangnetIntegration, UUID> {

    List<SabangnetIntegration> findAllByOrderByCreatedAtDesc();

    List<SabangnetIntegration> findByEnabledTrueOrderByCreatedAtDesc();

    Optional<SabangnetIntegration> findByIntegrationIdAndEnabledTrue(UUID integrationId);

    boolean existsByCompanyCodeIgnoreCaseAndSabangnetIdIgnoreCaseAndMallCodeIgnoreCase(
        String companyCode,
        String sabangnetId,
        String mallCode
    );
}
