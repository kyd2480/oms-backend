package com.oms.collector.controller;

import com.oms.collector.entity.ProductWarehouseStock;
import com.oms.collector.repository.ProductWarehouseStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 창고별 상품 재고 API
 *
 * GET /api/warehouse-stocks/{productId}           - 특정 상품의 창고별 재고
 * GET /api/warehouse-stocks/by-warehouse/{code}   - 특정 창고의 전체 상품 재고
 */
@Slf4j
@RestController
@RequestMapping("/api/warehouse-stocks")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class WarehouseStockController {

    private final ProductWarehouseStockRepository warehouseStockRepository;

    public static class StockDTO {
        public String  productId;
        public String  warehouseCode;
        public String  warehouseName;
        public Integer stock;

        public StockDTO(ProductWarehouseStock s) {
            this.productId     = s.getProductId().toString();
            this.warehouseCode = s.getWarehouseCode();
            this.warehouseName = s.getWarehouseName();
            this.stock         = s.getStock();
        }
    }

    // 특정 상품의 모든 창고 재고
    @GetMapping("/{productId}")
    @Transactional(readOnly = true)
    public ResponseEntity<List<StockDTO>> getByProduct(@PathVariable UUID productId) {
        return ResponseEntity.ok(
            warehouseStockRepository.findByProductId(productId)
                .stream().map(StockDTO::new).collect(Collectors.toList())
        );
    }

    // 특정 창고의 모든 상품 재고
    @GetMapping("/by-warehouse/{warehouseCode}")
    @Transactional(readOnly = true)
    public ResponseEntity<List<StockDTO>> getByWarehouse(@PathVariable String warehouseCode) {
        return ResponseEntity.ok(
            warehouseStockRepository.findByWarehouseCode(warehouseCode)
                .stream().map(StockDTO::new).collect(Collectors.toList())
        );
    }

    // 여러 상품의 창고별 재고를 한번에 조회 (재고목록 화면용)
    // POST /api/warehouse-stocks/batch { productIds: ["uuid1", "uuid2", ...] }
    @PostMapping("/batch")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, List<StockDTO>>> getBatch(
            @RequestBody Map<String, List<String>> body) {
        List<String> ids = body.get("productIds");
        if (ids == null || ids.isEmpty()) return ResponseEntity.ok(Map.of());

        return ResponseEntity.ok(
            ids.stream()
                .filter(id -> {
                    try { UUID.fromString(id); return true; }
                    catch (Exception e) { return false; }
                })
                .collect(Collectors.toMap(
                    id -> id,
                    id -> warehouseStockRepository.findByProductId(UUID.fromString(id))
                        .stream().map(StockDTO::new).collect(Collectors.toList())
                ))
        );
    }
}
