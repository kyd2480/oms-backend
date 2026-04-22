package com.oms.collector.repository;

import com.oms.collector.entity.SabangnetIntegration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SabangnetIntegrationRepository extends JpaRepository<SabangnetIntegration, UUID> {

    List<SabangnetIntegration> findAllByOrderByCreatedAtDesc();

    boolean existsByCompanyCodeIgnoreCaseAndSabangnetIdIgnoreCase(String companyCode, String sabangnetId);
}
