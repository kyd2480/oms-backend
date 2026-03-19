package com.oms.collector.controller;

import com.oms.collector.entity.Order;
import com.oms.collector.entity.OrderItem;
import com.oms.collector.entity.Product;
import com.oms.collector.repository.OrderRepository;
import com.oms.collector.repository.ProductRepository;
import com.oms.collector.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 재고 할당 컨트롤러
 * - 창고를 선택하고 할당 창고를 저장
 * - 실제 재고 예약/차감은 재고 매칭(StockMatchingController)과 검수발송에서 처리
 *
 * GET  /api/allocation/current          - 현재 할당 창고 조회
 * POST /api/allocation/set-warehouse    - 할당 창고 설정
 * POST /api/allocation/confirm/{orderNo}- 검수발송 시 실제 차감
 * POST /api/allocation/release/{orderNo}- 할당 취소
 */
@Slf4j
@RestController
@RequestMapping("/api/allocation")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AllocationController {

    private final OrderRepository   orderRepository;
    private final ProductRepository productRepository;
    private final InventoryService  inventoryService;

    // ─── 인메모리 창고 설정 저장 (간단한 구현) ────────────────
    // 실제 운영 시 DB 테이블로 관리 권장
    public static String selectedWarehouseCode = null;
    public static String selectedWarehouseName = null;

    /** 현재 선택된 창고 코드 반환 (다른 컨트롤러에서 참조용) */
    public static String getCurrentWarehouseCode() { return selectedWarehouseCode; }
    public static String getCurrentWarehouseName() { return selectedWarehouseName; }

    /**
     * 현재 할당 창고 조회
     * GET /api/allocation/current
     */
    @GetMapping("/current")
    public ResponseEntity<Map<String, Object>> getCurrent() {
        return ResponseEntity.ok(Map.of(
            "warehouseCode", selectedWarehouseCode != null ? selectedWarehouseCode : "",
            "warehouseName", selectedWarehouseName != null ? selectedWarehouseName : "",
            "isSet",         selectedWarehouseCode != null
        ));
    }

    /**
     * 할당 창고 설정
     * POST /api/allocation/set-warehouse
     * Body: { "warehouseCode": "ANYANG", "warehouseName": "본사 (안양)" }
     */
    @PostMapping("/set-warehouse")
    public ResponseEntity<Map<String, Object>> setWarehouse(@RequestBody Map<String, String> body) {
        String code = body.get("warehouseCode");
        String name = body.get("warehouseName");

        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "창고 코드 필요"));
        }

        selectedWarehouseCode = code;
        selectedWarehouseName = name != null ? name : code;

        log.info("할당 창고 설정: {} ({})", selectedWarehouseName, selectedWarehouseCode);
        return ResponseEntity.ok(Map.of(
            "success",       true,
            "warehouseCode", selectedWarehouseCode,
            "warehouseName", selectedWarehouseName,
            "message",       selectedWarehouseName + " 창고로 설정 완료"
        ));
    }

    /**
     * 검수발송 시 실제 재고 차감
     * POST /api/allocation/confirm/{orderNo}
     */
    @PostMapping("/confirm/{orderNo}")
    @Transactional
    public ResponseEntity<Map<String, Object>> confirmAndDeduct(@PathVariable String orderNo) {
        if (selectedWarehouseCode == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "할당 창고가 설정되지 않았습니다"));
        }

        log.info("재고 실차감 (검수발송): {} / 창고: {}", orderNo, selectedWarehouseCode);

        Order order = orderRepository.findByOrderNo(orderNo)
            .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다: " + orderNo));

        order.getItems().size();

        for (OrderItem item : order.getItems()) {
            Product product = findProduct(item);
            if (product == null) continue;
            int qty = item.getQuantity() != null ? item.getQuantity() : 0;
            try {
                // confirmReservedStock: totalStock-qty, reservedStock-qty
                // (재고매칭에서 reserveStock으로 예약했으므로 확정 시 예약→출고 처리)
                inventoryService.processOutboundWithWarehouse(
                    product.getProductId(), qty, selectedWarehouseCode,
                    order.getOrderId(), "검수발송 출고: " + orderNo
                );
                // 예약 재고 해제 (reservedStock 복구)
                inventoryService.releaseReservedStock(product.getProductId(), qty);
            } catch (Exception e) {
                log.error("재고 차감 실패: {} - {}", orderNo, e.getMessage());
            }
        }

        order.setOrderStatus(Order.OrderStatus.SHIPPED);
        orderRepository.save(order);

        return ResponseEntity.ok(Map.of("success", true, "message", "재고 차감 및 발송 처리 완료"));
    }

    /**
     * 할당 취소 (예약 해제)
     * POST /api/allocation/release/{orderNo}
     */
    @PostMapping("/release/{orderNo}")
    @Transactional
    public ResponseEntity<Map<String, Object>> release(@PathVariable String orderNo) {
        Order order = orderRepository.findByOrderNo(orderNo)
            .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다: " + orderNo));

        order.getItems().size();

        for (OrderItem item : order.getItems()) {
            Product product = findProduct(item);
            if (product == null) continue;
            int qty = item.getQuantity() != null ? item.getQuantity() : 0;
            try {
                inventoryService.releaseReservedStock(product.getProductId(), qty);
            } catch (Exception e) {
                log.error("재고 예약 해제 실패: {}", e.getMessage());
            }
        }

        order.setOrderStatus(Order.OrderStatus.PENDING);
        orderRepository.save(order);

        return ResponseEntity.ok(Map.of("success", true, "message", "할당 취소 완료"));
    }

    private Product findProduct(OrderItem item) {
        if (item.getProductCode() == null || item.getProductCode().isBlank()) return null;
        List<Product> found = productRepository.searchProducts(item.getProductCode());
        return found.stream()
            .filter(p -> item.getProductCode().equalsIgnoreCase(p.getSku())
                      || item.getProductCode().equalsIgnoreCase(p.getBarcode()))
            .findFirst().orElse(found.isEmpty() ? null : found.get(0));
    }
}
