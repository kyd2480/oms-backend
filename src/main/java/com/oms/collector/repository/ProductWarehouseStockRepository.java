package com.oms.collector.repository;

import com.oms.collector.entity.ProductWarehouseStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductWarehouseStockRepository extends JpaRepository<ProductWarehouseStock, UUID> {

    Optional<ProductWarehouseStock> findByProductIdAndWarehouseCode(UUID productId, String warehouseCode);

    List<ProductWarehouseStock> findByProductId(UUID productId);

    List<ProductWarehouseStock> findByWarehouseCode(String warehouseCode);

    @Query("SELECT SUM(s.stock) FROM ProductWarehouseStock s WHERE s.productId = :productId")
    Integer sumStockByProductId(@Param("productId") UUID productId);
}
