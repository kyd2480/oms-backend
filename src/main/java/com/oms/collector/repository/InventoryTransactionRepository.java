package com.oms.collector.repository;

import com.oms.collector.entity.InventoryTransaction;
import com.oms.collector.entity.Product;
import org.springframework.data.domain.Page;
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
     * 상품별 거래 내역 조회
     */
    Page<InventoryTransaction> findByProductOrderByCreatedAtDesc(Product product, Pageable pageable);
    
    /**
     * 거래 유형별 조회
     */
    List<InventoryTransaction> findByTransactionTypeOrderByCreatedAtDesc(String transactionType);
    
    /**
     * 기간별 거래 내역
     */
    List<InventoryTransaction> findByCreatedAtBetweenOrderByCreatedAtDesc(
        LocalDateTime startDate, 
        LocalDateTime endDate
    );
    
    /**
     * 상품 + 기간별 거래 내역
     */
    List<InventoryTransaction> findByProductAndCreatedAtBetweenOrderByCreatedAtDesc(
        Product product,
        LocalDateTime startDate,
        LocalDateTime endDate
    );
    
    /**
     * 최근 거래 내역 조회 (Limit)
     */
    @Query(value = "SELECT * FROM inventory_transactions ORDER BY created_at DESC LIMIT :limit", nativeQuery = true)
    List<InventoryTransaction> findTopNByOrderByCreatedAtDesc(@Param("limit") int limit);
    
    /**
     * 거래 내역 검색 (상품명, SKU, 바코드)
     */
    @Query(value = "SELECT t.* FROM inventory_transactions t " +
           "JOIN products p ON t.product_id = p.product_id " +
           "WHERE (LOWER(p.product_name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(p.barcode) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY t.created_at DESC LIMIT :limit", 
           nativeQuery = true)
    List<InventoryTransaction> searchTransactionsByProductKeyword(
        @Param("keyword") String keyword, 
        @Param("limit") int limit
    );
}
