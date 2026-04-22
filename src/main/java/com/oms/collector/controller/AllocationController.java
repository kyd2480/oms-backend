package com.oms.collector.controller;

import com.oms.collector.config.TenantContext;
import com.oms.collector.entity.Order;
import com.oms.collector.entity.OrderItem;
import com.oms.collector.entity.Product;
import com.oms.collector.repository.OrderRepository;
import com.oms.collector.repository.ProductRepository;
import com.oms.collector.service.InventoryService;
import com.oms.collector.service.WorkLockService;
import com.oms.collector.service.market.MarketShipmentSyncService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
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
@DependsOn("operationalSchemaMigration")
public class AllocationController {
    private static final String INVOICE_PREFIX = "INVOICE:";
    private static final String SETTING_WAREHOUSE_CODE = "allocation.warehouseCode";
    private static final String SETTING_WAREHOUSE_NAME = "allocation.warehouseName";

    private record InvoiceInfo(String carrierCode, String carrierName, String trackingNo) {}

    private static final int SCAN_LOCK_TTL = 15; // 초

    private final OrderRepository   orderRepository;
    private final ProductRepository productRepository;
    private final InventoryService  inventoryService;
    private final MarketShipmentSyncService marketShipmentSyncService;
    private final JdbcTemplate jdbcTemplate;
    private final WorkLockService workLockService;

    // ─── 인메모리 창고 설정 저장 (간단한 구현) ────────────────
    // 실제 운영 시 DB 테이블로 관리 권장
    public static String selectedWarehouseCode = null;
    public static String selectedWarehouseName = null;

    /** 현재 선택된 창고 코드 반환 (다른 컨트롤러에서 참조용) */
    public static String getCurrentWarehouseCode() { return selectedWarehouseCode; }
    public static String getCurrentWarehouseName() { return selectedWarehouseName; }

    @PostConstruct
    public void loadPersistedWarehouse() {
        refreshSelectedWarehouseFromDb();
    }

    /**
     * 현재 할당 창고 조회
     * GET /api/allocation/current
     */
    @GetMapping("/current")
    public ResponseEntity<Map<String, Object>> getCurrent() {
        refreshSelectedWarehouseFromDb();
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
        saveSetting(SETTING_WAREHOUSE_CODE, selectedWarehouseCode);
        saveSetting(SETTING_WAREHOUSE_NAME, selectedWarehouseName);

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
     * Body(optional): { "warehouseCode": "ANYANG", "warehouseName": "본사(안양)" }
     */
    @PostMapping("/confirm/{orderNo}")
    @Transactional
    public ResponseEntity<Map<String, Object>> confirmAndDeduct(
            @PathVariable String orderNo,
            @RequestBody(required = false) Map<String, String> body) {

        // 요청 body에 창고 코드가 있으면 우선 사용, 없으면 static 변수 사용
        String warehouseCode = (body != null && body.get("warehouseCode") != null && !body.get("warehouseCode").isBlank())
            ? body.get("warehouseCode") : selectedWarehouseCode;
        String warehouseName = (body != null && body.get("warehouseName") != null && !body.get("warehouseName").isBlank())
            ? body.get("warehouseName") : selectedWarehouseName;

        if (warehouseCode == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "할당 창고가 설정되지 않았습니다"));
        }

        // static 변수도 최신 값으로 업데이트
        selectedWarehouseCode = warehouseCode;
        if (warehouseName != null) selectedWarehouseName = warehouseName;
        saveSetting(SETTING_WAREHOUSE_CODE, selectedWarehouseCode);
        saveSetting(SETTING_WAREHOUSE_NAME, selectedWarehouseName != null ? selectedWarehouseName : selectedWarehouseCode);

        log.info("재고 실차감 (검수발송): {} / 창고: {}", orderNo, warehouseCode);

        Order order = orderRepository.findByOrderNo(orderNo)
            .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다: " + orderNo));

        if (order.getOrderStatus() != Order.OrderStatus.CONFIRMED) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "CONFIRMED 상태 주문만 검수출고할 수 있습니다: " + order.getOrderStatus()
            ));
        }

        if (Boolean.TRUE.equals(order.getShippingHold())) {
            boolean forceHold = body != null && "true".equalsIgnoreCase(String.valueOf(body.get("forceHold")));
            boolean releaseHold = body != null && "true".equalsIgnoreCase(String.valueOf(body.get("releaseHold")));
            if (!forceHold && !releaseHold) {
                return ResponseEntity.status(409).body(Map.of(
                    "success", false,
                    "hold", true,
                    "message", "출고보류 주문입니다. 보류해지 후 출고할지 확인해주세요: " + (order.getHoldReason() != null ? order.getHoldReason() : "클레임 확인 필요")
                ));
            }
            if (releaseHold) {
                order.setShippingHold(false);
                order.setHoldReason(null);
            }
        }

        if (Boolean.TRUE.equals(order.getShippingHold())) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "출고보류 주문입니다: " + (order.getHoldReason() != null ? order.getHoldReason() : "클레임 확인 필요")
            ));
        }

        InvoiceInfo invoiceInfo = extractInvoiceInfo(order.getDeliveryMemo());
        if (invoiceInfo == null || invoiceInfo.trackingNo() == null || invoiceInfo.trackingNo().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "송장출력 페이지에서 송장번호를 먼저 발급한 뒤 검수출고할 수 있습니다"
            ));
        }

        // 같은 송장번호 단위로만 짧은 락 (서로 다른 송장은 동시 처리 가능)
        String scanLockKey = "INVOICE_SCAN:" + invoiceInfo.trackingNo();
        workLockService.acquire(scanLockKey, TenantContext.getCurrentUser(), SCAN_LOCK_TTL);
        try {

        order.getItems().size();

        for (OrderItem item : order.getItems()) {
            Product product = findProduct(item);
            if (product == null) continue;
            int qty = item.getActiveQuantity();
            if (qty <= 0) continue;
            try {
                inventoryService.processOutboundWithWarehouse(
                    product.getProductId(), qty, warehouseCode,
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

        MarketShipmentSyncService.MarketShipmentSyncResult syncResult = marketShipmentSyncService.syncShipment(
            order,
            invoiceInfo.carrierCode(),
            invoiceInfo.carrierName(),
            invoiceInfo.trackingNo()
        );

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "재고 차감 및 발송 처리 완료",
            "marketSyncSuccess", syncResult.success(),
            "marketSyncMessage", syncResult.message()
        ));
        } finally {
            workLockService.release(scanLockKey);
        }
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
            int qty = item.getActiveQuantity();
            if (qty <= 0) continue;
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

    private void refreshSelectedWarehouseFromDb() {
        String code = readSetting(SETTING_WAREHOUSE_CODE);
        if (code == null || code.isBlank()) {
            return;
        }
        selectedWarehouseCode = code;
        String name = readSetting(SETTING_WAREHOUSE_NAME);
        selectedWarehouseName = (name != null && !name.isBlank()) ? name : code;
    }

    private String readSetting(String key) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT setting_value FROM operational_settings WHERE setting_key = ?",
                String.class,
                key
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private void saveSetting(String key, String value) {
        jdbcTemplate.update("""
            INSERT INTO operational_settings (setting_key, setting_value, updated_at)
            VALUES (?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (setting_key)
            DO UPDATE SET setting_value = EXCLUDED.setting_value,
                          updated_at = CURRENT_TIMESTAMP
            """, key, value);
    }

    private static InvoiceInfo extractInvoiceInfo(String memo) {
        if (memo == null || memo.isBlank()) {
            return null;
        }
        int invoiceIndex = memo.indexOf(INVOICE_PREFIX);
        if (invoiceIndex < 0) {
            return null;
        }

        String carrierCode = null;
        String carrierName = null;
        String trackingNo = null;
        String invoiceSegment = memo.substring(invoiceIndex + INVOICE_PREFIX.length());
        for (String part : invoiceSegment.split("\\|")) {
            String[] kv = part.split(":", 2);
            if (kv.length != 2) {
                continue;
            }
            if ("CARRIER".equals(kv[0])) carrierCode = kv[1];
            if ("CARRIER_NAME".equals(kv[0])) carrierName = kv[1];
            if ("TRACKING".equals(kv[0])) trackingNo = kv[1];
        }
        return new InvoiceInfo(carrierCode, carrierName, trackingNo);
    }
}
