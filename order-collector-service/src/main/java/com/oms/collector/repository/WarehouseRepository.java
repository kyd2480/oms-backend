package com.oms.collector.repository;

import com.oms.collector.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 창고 Repository
 */
@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {

    Optional<Warehouse> findByCode(String code);

    boolean existsByCode(String code);

    // 활성 창고만 (정렬 순서 → 이름 순)
    List<Warehouse> findByIsActiveTrueOrderBySortOrderAscNameAsc();

    // 전체 창고 (정렬 순서 → 이름 순)
    List<Warehouse> findAllByOrderBySortOrderAscNameAsc();

    // 유형별 활성 창고
    List<Warehouse> findByTypeAndIsActiveTrueOrderBySortOrderAscNameAsc(String type);

    // 이름 검색
    @Query("SELECT w FROM Warehouse w WHERE " +
           "LOWER(w.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(w.code) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "ORDER BY w.sortOrder ASC, w.name ASC")
    List<Warehouse> searchByKeyword(String keyword);
}
