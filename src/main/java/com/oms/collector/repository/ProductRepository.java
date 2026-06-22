package com.oms.collector.repository;

import com.oms.collector.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 상품 Repository
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findBySku(String sku);

    Optional<Product> findByBarcode(String barcode);

    Optional<Product> findByBarcode2(String barcode2);

    @Query("SELECT p FROM Product p WHERE p.barcode = :barcode OR p.barcode2 = :barcode")
    List<Product> findByBarcodeOrBarcode2(@Param("barcode") String barcode);

    boolean existsBySku(String sku);

    List<Product> findByIsActiveTrueOrderByProductNameAsc();

    @Query("SELECT p FROM Product p WHERE p.availableStock = 0 AND p.isActive = true")
    List<Product> findLowStockProducts();

    @Query("SELECT p FROM Product p WHERE p.availableStock = 0 AND p.isActive = true")
    List<Product> findOutOfStockProducts();

    List<Product> findByCategoryAndIsActiveTrueOrderByProductNameAsc(String category);

    List<Product> findByProductNameContainingIgnoreCaseAndIsActiveTrue(String keyword);

    @Query("SELECT p FROM Product p WHERE " +
           "(p.sku LIKE %:keyword% OR p.barcode LIKE %:keyword% OR p.barcode2 LIKE %:keyword%) " +
           "AND p.isActive = true")
    List<Product> findBySkuOrBarcodeContaining(String keyword);

    @Query("SELECT p FROM Product p WHERE " +
           "(LOWER(p.productName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(p.barcode) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(p.barcode2) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(p.color) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND p.isActive = true " +
           "ORDER BY p.productName ASC")
    List<Product> searchProducts(String keyword);

    @Query("SELECT p FROM Product p WHERE LOWER(p.sku) IN :codes OR LOWER(p.barcode) IN :codes OR LOWER(p.barcode2) IN :codes")
    List<Product> findBySkuOrBarcodeInLowercase(@Param("codes") List<String> codes);

    @Query("SELECT COUNT(p) FROM Product p WHERE COALESCE(p.availableStock, 0) < 0 AND p.isActive = true")
    long countNegativeAvailableStock();

    @Query("SELECT p FROM Product p WHERE COALESCE(p.availableStock, 0) < 0 AND p.isActive = true " +
           "ORDER BY p.availableStock ASC, p.updatedAt DESC")
    List<Product> findTop10NegativeAvailableStock();
}
