package com.oms.collector.repository;

import com.oms.collector.entity.InventoryTransaction;
import com.oms.collector.entity.Product;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 재고 거래 내역 Repository
 */
@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, UUID> {
    
    /**
     * 상품 + 기간별 거래 내역
     */
    List<InventoryTransaction> findByProductAndCreatedAtBetweenOrderByCreatedAtDesc(
        Product product,
        LocalDateTime startDate,
        LocalDateTime endDate
    );
    
    /**
     * 최근 거래 내역 조회
     */
    List<InventoryTransaction> findAllByOrderByCreatedAtDesc(Pageable pageable);
    
    /**
     * 거래 내역 검색 (상품명, SKU, 바코드)
     */
    @Query("SELECT t FROM InventoryTransaction t " +
           "WHERE LOWER(t.product.productName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(t.product.sku) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(t.product.barcode) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "ORDER BY t.createdAt DESC")
    List<InventoryTransaction> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
