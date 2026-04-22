package com.oms.collector.repository;

import com.oms.collector.entity.CarrierContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CarrierContractRepository extends JpaRepository<CarrierContract, UUID> {
    List<CarrierContract> findAllByOrderByCarrierCodeAscCreatedAtDesc();
    List<CarrierContract> findByCompanyCodeIgnoreCaseAndCarrierCodeIgnoreCase(String companyCode, String carrierCode);
    Optional<CarrierContract> findFirstByCompanyCodeIgnoreCaseAndCarrierCodeIgnoreCaseAndIsDefaultTrue(String companyCode, String carrierCode);
}
