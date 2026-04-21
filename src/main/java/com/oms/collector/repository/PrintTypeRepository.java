package com.oms.collector.repository;

import com.oms.collector.entity.PrintType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PrintTypeRepository extends JpaRepository<PrintType, UUID> {
    Optional<PrintType> findByCode(String code);
    boolean existsByCode(String code);
    List<PrintType> findAllByOrderBySortOrderAscNameAsc();
    List<PrintType> findByIsActiveTrueOrderBySortOrderAscNameAsc();

    @Query("SELECT p FROM PrintType p WHERE " +
           "LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.code) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "ORDER BY p.sortOrder ASC, p.name ASC")
    List<PrintType> searchByKeyword(String keyword);
}
