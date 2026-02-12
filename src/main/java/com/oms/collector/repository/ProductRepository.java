package com.oms.collector.repository;

import com.oms.collector.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 상품 Repository
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    
    /**
     * SKU로 상품 조회
     */
    Optional<Product> findBySku(String sku);
    
    /**
     * SKU 존재 여부
     */
    boolean existsBySku(String sku);
    
    /**
     * 활성 상품 목록
     */
    List<Product> findByIsActiveTrueOrderByProductNameAsc();
    
    /**
     * 안전 재고 미달 상품 조회
     */
    @Query("SELECT p FROM Product p WHERE p.availableStock <= p.safetyStock AND p.isActive = true")
    List<Product> findLowStockProducts();
    
    /**
     * 재고 없는 상품 조회
     */
    @Query("SELECT p FROM Product p WHERE p.availableStock = 0 AND p.isActive = true")
    List<Product> findOutOfStockProducts();
    
    /**
     * 카테고리별 상품 조회
     */
    List<Product> findByCategoryAndIsActiveTrueOrderByProductNameAsc(String category);
    
    /**
     * 상품명으로 검색
     */
    List<Product> findByProductNameContainingIgnoreCaseAndIsActiveTrue(String keyword);
    
    /**
     * SKU 또는 바코드로 검색
     */
    @Query("SELECT p FROM Product p WHERE (p.sku LIKE %:keyword% OR p.barcode LIKE %:keyword%) AND p.isActive = true")
    List<Product> findBySkuOrBarcodeContaining(String keyword);
}
