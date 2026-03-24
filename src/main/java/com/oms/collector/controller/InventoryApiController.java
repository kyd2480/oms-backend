package com.oms.collector.controller;

import com.oms.collector.entity.InventoryTransaction;
import com.oms.collector.entity.Product;
import com.oms.collector.repository.ProductRepository;
import com.oms.collector.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Android 앱 연동용 재고 입출고 API
 *
 * GET  /api/inventory/scan/{barcode}   - 바코드로 상품 조회
 * GET  /api/inventory/search           - 상품 검색
 * POST /api/inventory/inbound          - 입고 처리
 * POST /api/inventory/outbound         - 출고 처리
 * GET  /api/inventory/transactions     - 입출고 내역
 * GET  /api/inventory/summary          - 재고 현황 요약
 */
@Slf4j
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class InventoryApiController {

    private final ProductRepository productRepository;
    private final InventoryService  inventoryService;

    /* ── DTO ─────────────────────────────────────────────── */

    public static class ProductDTO {
        public String  productId;
        public String  sku;
        public String  barcode;
        public String  productName;
        public int     totalStock;
        public int     availableStock;
        public int     reservedStock;
        public int     warehouseStockAnyang;
        public int     warehouseStockIcheon;
        public int     warehouseStockBucheon;
        public String  warehouseLocation;
        public boolean isActive;

        public ProductDTO(Product p) {
            this.productId             = p.getProductId().toString();
            this.sku                   = p.getSku();
            this.barcode               = p.getBarcode();
            this.productName           = p.getProductName();
            this.totalStock            = p.getTotalStock()            != null ? p.getTotalStock()            : 0;
            this.availableStock        = p.getAvailableStock()        != null ? p.getAvailableStock()        : 0;
            this.reservedStock         = p.getReservedStock()         != null ? p.getReservedStock()         : 0;
            this.warehouseStockAnyang  = p.getWarehouseStockAnyang()  != null ? p.getWarehouseStockAnyang()  : 0;
            this.warehouseStockIcheon  = p.getWarehouseStockIcheon()  != null ? p.getWarehouseStockIcheon()  : 0;
            this.warehouseStockBucheon = p.getWarehouseStockBucheon() != null ? p.getWarehouseStockBucheon() : 0;
            this.warehouseLocation     = p.getWarehouseLocation();
            this.isActive              = Boolean.TRUE.equals(p.getIsActive());
        }
    }

    public static class InboundRequest {
        public String productId;     // UUID
        public int    quantity;      // 입고 수량
        public String warehouseCode; // ANYANG | ICHEON | BUCHEON
        public String notes;
    }

    public static class OutboundRequest {
        public String productId;
        public int    quantity;
        public String warehouseCode;
        public String notes;
    }

    public static class TransactionDTO {
        public String transactionId;
        public String type;
        public String productName;
        public String sku;
        public int    quantity;
        public int    beforeStock;
        public int    afterStock;
        public String notes;
        public String createdAt;

        public TransactionDTO(InventoryTransaction t) {
            this.transactionId = t.getTransactionId() != null ? t.getTransactionId().toString() : "";
            this.type          = t.getTransactionType() != null ? t.getTransactionType().name() : "";
            this.productName   = t.getProduct() != null ? t.getProduct().getProductName() : "";
            this.sku           = t.getProduct() != null ? t.getProduct().getSku() : "";
            this.quantity      = t.getQuantity()    != null ? t.getQuantity()    : 0;
            this.beforeStock   = t.getBeforeStock() != null ? t.getBeforeStock() : 0;
            this.afterStock    = t.getAfterStock()  != null ? t.getAfterStock()  : 0;
            this.notes         = t.getNotes();
            this.createdAt     = t.getCreatedAt() != null ? t.getCreatedAt().toString() : "";
        }
    }

    /* ── 바코드 스캔 → 상품 조회 ──────────────────────────── */

    @GetMapping("/scan/{barcode}")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> scan(@PathVariable String barcode) {
        log.info("바코드 스캔: {}", barcode);

        List<Product> found = productRepository.searchProducts(barcode);
        Product product = found.stream()
            .filter(p -> barcode.equalsIgnoreCase(p.getSku())
                      || barcode.equalsIgnoreCase(p.getBarcode()))
            .findFirst()
            .orElse(found.isEmpty() ? null : found.get(0));

        if (product == null) {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "message", "등록되지 않은 바코드입니다: " + barcode
            ));
        }

        return ResponseEntity.ok(Map.of(
            "success", true,
            "product", new ProductDTO(product)
        ));
    }

    /* ── 상품 검색 ────────────────────────────────────────── */

    @GetMapping("/search")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ProductDTO>> search(
        @RequestParam String keyword,
        @RequestParam(defaultValue = "30") int limit
    ) {
        return ResponseEntity.ok(
            productRepository.searchProducts(keyword).stream()
                .limit(limit)
                .map(ProductDTO::new)
                .collect(Collectors.toList())
        );
    }

    /* ── 입고 처리 ────────────────────────────────────────── */

    @PostMapping("/inbound")
    @Transactional
    public ResponseEntity<Map<String, Object>> inbound(@RequestBody InboundRequest req) {
        log.info("입고: productId={}, qty={}, warehouse={}", req.productId, req.quantity, req.warehouseCode);

        if (req.productId == null || req.quantity <= 0) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "productId와 quantity(>0)가 필요합니다"
            ));
        }

        try {
            UUID   id        = UUID.fromString(req.productId);
            String warehouse = req.warehouseCode != null ? req.warehouseCode.toUpperCase() : "ANYANG";
            String notes     = req.notes != null ? req.notes : "Android 앱 입고";

            Product updated = inventoryService.processInboundWithWarehouse(
                id, req.quantity, warehouse, null, notes
            );

            log.info("입고 완료: {} +{}개 ({}창고)", updated.getSku(), req.quantity, warehouse);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", updated.getProductName() + " " + req.quantity + "개 입고 완료",
                "product", new ProductDTO(updated)
            ));
        } catch (Exception e) {
            log.error("입고 실패: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("success", false, "message", "입고 실패: " + e.getMessage()));
        }
    }

    /* ── 출고 처리 ────────────────────────────────────────── */

    @PostMapping("/outbound")
    @Transactional
    public ResponseEntity<Map<String, Object>> outbound(@RequestBody OutboundRequest req) {
        log.info("출고: productId={}, qty={}, warehouse={}", req.productId, req.quantity, req.warehouseCode);

        if (req.productId == null || req.quantity <= 0) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "productId와 quantity(>0)가 필요합니다"
            ));
        }

        try {
            UUID   id        = UUID.fromString(req.productId);
            String warehouse = req.warehouseCode != null ? req.warehouseCode.toUpperCase() : "ANYANG";
            String notes     = req.notes != null ? req.notes : "Android 앱 출고";

            Product updated = inventoryService.processOutboundWithWarehouse(
                id, req.quantity, warehouse, null, notes
            );

            log.info("출고 완료: {} -{}개 ({}창고)", updated.getSku(), req.quantity, warehouse);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", updated.getProductName() + " " + req.quantity + "개 출고 완료",
                "product", new ProductDTO(updated)
            ));
        } catch (IllegalStateException e) {
            // 재고 부족
            return ResponseEntity.ok(Map.of("success", false, "message", "재고 부족: " + e.getMessage()));
        } catch (Exception e) {
            log.error("출고 실패: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("success", false, "message", "출고 실패: " + e.getMessage()));
        }
    }

    /* ── 입출고 내역 조회 ─────────────────────────────────── */

    @GetMapping("/transactions")
    @Transactional(readOnly = true)
    public ResponseEntity<List<TransactionDTO>> transactions(
        @RequestParam(defaultValue = "50") int limit
    ) {
        return ResponseEntity.ok(
            inventoryService.getRecentTransactions(limit).stream()
                .map(TransactionDTO::new)
                .collect(Collectors.toList())
        );
    }

    /* ── 재고 현황 요약 ───────────────────────────────────── */

    @GetMapping("/summary")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> summary() {
        List<Product> all = productRepository.findAll();
        long total      = all.size();
        long outOfStock = all.stream().filter(p -> p.getAvailableStock() != null && p.getAvailableStock() <= 0).count();
        long lowStock   = all.stream().filter(p -> p.isBelowSafetyStock()).count();
        int  totalQty   = all.stream().mapToInt(p -> p.getTotalStock() != null ? p.getTotalStock() : 0).sum();

        return ResponseEntity.ok(Map.of(
            "totalProducts",  total,
            "outOfStock",     outOfStock,
            "lowStock",       lowStock,
            "totalQuantity",  totalQty
        ));
    }
}
