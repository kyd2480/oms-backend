package com.oms.collector.controller;

import com.oms.collector.entity.Product;
import com.oms.collector.entity.ProductReturn;
import com.oms.collector.repository.ProductReturnRepository;
import com.oms.collector.service.InventoryService;
import com.oms.collector.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 반품 관리 API
 *
 * POST /api/returns              - 반품 접수
 * GET  /api/returns              - 반품 목록
 * GET  /api/returns/{id}         - 반품 상세
 * PUT  /api/returns/{id}/inspect - 검수 처리 (정상/불량 판정 + 입고)
 * PUT  /api/returns/{id}/resolve - 환불/교환 메모 저장
 * PUT  /api/returns/{id}/cancel  - 반품 취소
 * GET  /api/returns/stats        - 통계
 */
@Slf4j
@RestController
@RequestMapping("/api/returns")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ReturnController {

    private final ProductReturnRepository  returnRepository;
    private final InventoryService  inventoryService;
    private final ProductRepository productRepository;

    /* ── DTO ─────────────────────────────────────────── */

    public static class ReturnCreateRequest {
        public String orderNo;
        public String channelName;
        public String recipientName;
        public String recipientPhone;
        public String productName;
        public Integer quantity;
        public String returnType;    // CANCEL | REFUND | EXCHANGE
        public String returnReason;
        public String returnTrackingNo;
        public String carrierName;
    }

    public static class InspectRequest {
        public String inspectResult;   // NORMAL | DEFECTIVE
        public String warehouseCode;
        public String inspectMemo;
        // 단일 (하위호환)
        public String  productSku;
        public Integer restockQty;
        // 복수 상품 (체크박스 다중 선택)
        public List<RestockItem> restockItems;

        public static class RestockItem {
            public String  productSku;    // SKU (있으면 우선)
            public String  productName;   // 상품명
            public String  optionName;    // 옵션명
            public Integer quantity;
            public String  itemResult;    // NORMAL | DEFECTIVE (상품별 판정)
            public String  warehouseCode; // 상품별 입고 창고 (프론트에서 계산해서 전달)
        }
    }

    public static class ResolveRequest {
        public String resolutionType;  // REFUND | EXCHANGE | NONE
        public Integer refundAmount;
        public String exchangeOrderNo;
        public String resolutionMemo;
    }

    public static class ReturnDTO {
        public String  returnId;
        public String  orderNo;
        public String  channelName;
        public String  recipientName;
        public String  recipientPhone;
        public String  productName;
        public Integer quantity;
        public String  returnType;
        public String  returnReason;
        public String  returnTrackingNo;
        public String  carrierName;
        public String  status;
        public String  inspectResult;
        public String  warehouseCode;
        public String  inspectMemo;
        public String  resolutionType;
        public Integer refundAmount;
        public String  exchangeOrderNo;
        public String  resolutionMemo;
        public String  source;
        public String  createdAt;
        public String  updatedAt;
        public String  completedAt;
        public String  stockedItems;  // 입고 내역 JSON (취소 시 차감용)

        public ReturnDTO(ProductReturn r) {
            this.returnId        = r.getReturnId().toString();
            this.orderNo         = r.getOrderNo();
            this.channelName     = r.getChannelName();
            this.recipientName   = r.getRecipientName();
            this.recipientPhone  = r.getRecipientPhone();
            this.productName     = r.getProductName();
            this.quantity        = r.getQuantity();
            this.returnType      = r.getReturnType()      != null ? r.getReturnType().name()      : null;
            this.returnReason    = r.getReturnReason();
            this.returnTrackingNo= r.getReturnTrackingNo();
            this.carrierName     = r.getCarrierName();
            this.status          = r.getStatus()          != null ? r.getStatus().name()          : null;
            this.inspectResult   = r.getInspectResult()   != null ? r.getInspectResult().name()   : null;
            this.warehouseCode   = r.getWarehouseCode();
            this.inspectMemo     = r.getInspectMemo();
            this.resolutionType  = r.getResolutionType()  != null ? r.getResolutionType().name()  : null;
            this.refundAmount    = r.getRefundAmount();
            this.exchangeOrderNo = r.getExchangeOrderNo();
            this.resolutionMemo  = r.getResolutionMemo();
            this.source          = r.getSource();
            this.createdAt       = r.getCreatedAt()   != null ? r.getCreatedAt().toString()   : null;
            this.updatedAt       = r.getUpdatedAt()   != null ? r.getUpdatedAt().toString()   : null;
            this.completedAt     = r.getCompletedAt() != null ? r.getCompletedAt().toString() : null;
            this.stockedItems    = r.getStockedItems();
        }
    }

    /* ── 반품 접수 ────────────────────────────────────── */

    @PostMapping
    @Transactional
    public ResponseEntity<ReturnDTO> create(@RequestBody ReturnCreateRequest req) {
        log.info("반품 접수: orderNo={}, type={}", req.orderNo, req.returnType);

        ProductReturn ret = ProductReturn.builder()
            .orderNo(req.orderNo)
            .channelName(req.channelName)
            .recipientName(req.recipientName)
            .recipientPhone(req.recipientPhone)
            .productName(req.productName)
            .quantity(req.quantity != null ? req.quantity : 1)
            .returnType(ProductReturn.ReturnType.valueOf(req.returnType))
            .returnReason(req.returnReason)
            .returnTrackingNo(req.returnTrackingNo)
            .carrierName(req.carrierName)
            .status(ProductReturn.ReturnStatus.REQUESTED)
            .source("MANUAL")
            .build();

        return ResponseEntity.ok(new ReturnDTO(returnRepository.save(ret)));
    }

    /* ── 반품 목록 ────────────────────────────────────── */

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<ReturnDTO>> list(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String startDate,
        @RequestParam(required = false) String endDate
    ) {
        List<ProductReturn> returns;

        if (keyword != null && !keyword.isBlank()) {
            returns = returnRepository.searchByKeyword(keyword.trim());
        } else if (status != null && !status.isBlank() && !status.equals("ALL")) {
            returns = returnRepository.findByStatusOrderByCreatedAtDesc(
                ProductReturn.ReturnStatus.valueOf(status)
            );
        } else {
            returns = returnRepository.findAllByOrderByCreatedAtDesc();
        }

        return ResponseEntity.ok(
            returns.stream().map(ReturnDTO::new).collect(Collectors.toList())
        );
    }

    /* ── 반품 상세 ────────────────────────────────────── */

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<ReturnDTO> get(@PathVariable UUID id) {
        return returnRepository.findById(id)
            .map(r -> ResponseEntity.ok(new ReturnDTO(r)))
            .orElse(ResponseEntity.notFound().build());
    }

    /* ── 검수 처리 ────────────────────────────────────── */

    @PutMapping("/{id}/inspect")
    @Transactional
    public ResponseEntity<Map<String, Object>> inspect(
        @PathVariable UUID id,
        @RequestBody InspectRequest req
    ) {
        log.info("반품 검수: id={}, result={}", id, req.inspectResult);

        ProductReturn ret = returnRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("반품을 찾을 수 없습니다: " + id));

        ProductReturn.InspectResult result = ProductReturn.InspectResult.valueOf(req.inspectResult);
        ret.setInspectResult(result);
        ret.setInspectMemo(req.inspectMemo);
        ret.setWarehouseCode(req.warehouseCode);

        List<InspectRequest.RestockItem> items =
            (req.restockItems != null && !req.restockItems.isEmpty())
                ? req.restockItems : new ArrayList<>();

        // ── 1단계: 모든 상품 매칭 검증 (하나라도 실패하면 전체 중단) ──
        Map<InspectRequest.RestockItem, Product> matched = new LinkedHashMap<>();
        for (InspectRequest.RestockItem item : items) {
            if (item.quantity == null || item.quantity <= 0) continue;

            String searchKey = (item.productSku != null && !item.productSku.isBlank())
                ? item.productSku : item.productName;
            if (searchKey == null || searchKey.isBlank()) continue;

            List<Product> found = productRepository.searchProducts(searchKey);
            Product product = found.stream()
                .filter(p -> {
                    if (item.productSku != null && !item.productSku.isBlank()) {
                        return item.productSku.equalsIgnoreCase(p.getSku())
                            || item.productSku.equalsIgnoreCase(p.getBarcode());
                    }
                    return p.getProductName() != null &&
                           p.getProductName().contains(searchKey.trim());
                })
                .findFirst().orElse(found.isEmpty() ? null : found.get(0));

            if (product == null) {
                log.warn("반품 상품 미매칭: searchKey={}", searchKey);
                Map<String, Object> errRes = new LinkedHashMap<>();
                errRes.put("success", false);
                errRes.put("stockMessage",
                    "상품을 찾을 수 없습니다: [" + (item.productName != null ? item.productName : searchKey) + "]\n"
                    + "재고 관리에서 해당 상품의 상품명 또는 SKU를 확인해주세요.");
                return ResponseEntity.ok(errRes);
            }
            matched.put(item, product);
        }

        // ── 2단계: 전체 매칭 성공 → 입고 처리 + 내역 저장 ──
        List<String> stockMsgs  = new ArrayList<>();
        List<Map<String,Object>> stockedList = new ArrayList<>(); // 취소 시 차감용

        for (Map.Entry<InspectRequest.RestockItem, Product> e : matched.entrySet()) {
            InspectRequest.RestockItem item = e.getKey();
            Product product = e.getValue();

            String itemWarehouse = (item.warehouseCode != null && !item.warehouseCode.isBlank())
                ? item.warehouseCode : req.warehouseCode;

            String notes = "반품 입고 (" + ret.getOrderNo() + ") "
                + ("DEFECTIVE".equals(item.itemResult) ? "[불량]" : "[정상]");

            inventoryService.processInboundWithWarehouse(
                product.getProductId(), item.quantity, itemWarehouse, null, notes
            );

            // 취소 시 차감을 위해 내역 저장
            Map<String,Object> record = new LinkedHashMap<>();
            record.put("productId",    product.getProductId().toString());
            record.put("productName",  product.getProductName());
            record.put("sku",          product.getSku());
            record.put("quantity",     item.quantity);
            record.put("warehouseCode", itemWarehouse);
            record.put("itemResult",   item.itemResult);
            stockedList.add(record);

            stockMsgs.add(product.getProductName() + " " + item.quantity + "개 → " + itemWarehouse);
            log.info("반품 입고: {} {}개 → {}", product.getSku(), item.quantity, itemWarehouse);
        }

        // 입고 내역 JSON으로 저장
        try {
            ret.setStockedItems(new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(stockedList));
        } catch (Exception ex) {
            log.warn("stockedItems 직렬화 실패: {}", ex.getMessage());
        }

        // 불량 하나라도 → INSPECTING(환불/교환 대기), 전체 정상 → COMPLETED
        boolean hasDefective = items.stream().anyMatch(it -> "DEFECTIVE".equals(it.itemResult));
        if (hasDefective) {
            ret.setStatus(ProductReturn.ReturnStatus.INSPECTING);
        } else {
            ret.setStatus(ProductReturn.ReturnStatus.COMPLETED);
            ret.setCompletedAt(LocalDateTime.now());
        }

        returnRepository.save(ret);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("return", new ReturnDTO(ret));
        res.put("stockMessage", stockMsgs.isEmpty() ? "재고 처리 없음" : String.join(", ", stockMsgs));
        return ResponseEntity.ok(res);
    }

    /* ── 환불/교환 처리 메모 ──────────────────────────── */

    @PutMapping("/{id}/resolve")
    @Transactional
    public ResponseEntity<ReturnDTO> resolve(
        @PathVariable UUID id,
        @RequestBody ResolveRequest req
    ) {
        ProductReturn ret = returnRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("반품을 찾을 수 없습니다: " + id));

        ret.setResolutionType(ProductReturn.ResolutionType.valueOf(req.resolutionType));
        ret.setRefundAmount(req.refundAmount);
        ret.setExchangeOrderNo(req.exchangeOrderNo);
        ret.setResolutionMemo(req.resolutionMemo);
        ret.setStatus(ProductReturn.ReturnStatus.COMPLETED);
        ret.setCompletedAt(LocalDateTime.now());

        return ResponseEntity.ok(new ReturnDTO(returnRepository.save(ret)));
    }

    /* ── 반품 취소 ────────────────────────────────────── */

    @PutMapping("/{id}/cancel")
    @Transactional
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable UUID id) {
        ProductReturn ret = returnRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("반품을 찾을 수 없습니다: " + id));

        List<String> rollbackMsgs = new ArrayList<>();

        // 입고 내역이 있으면 출고 차감 (롤백)
        if (ret.getStockedItems() != null && !ret.getStockedItems().isBlank()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
                List<Map<String, Object>> stockedList = mapper.readValue(
                    ret.getStockedItems(),
                    mapper.getTypeFactory().constructCollectionType(List.class, Map.class)
                );

                for (Map<String, Object> record : stockedList) {
                    UUID productId    = UUID.fromString((String) record.get("productId"));
                    int  quantity     = ((Number) record.get("quantity")).intValue();
                    String warehouse  = (String) record.get("warehouseCode");
                    String productName= (String) record.get("productName");

                    try {
                        inventoryService.processOutboundWithWarehouse(
                            productId, quantity, warehouse, null,
                            "반품 취소 차감 (" + ret.getOrderNo() + ")"
                        );
                        rollbackMsgs.add(productName + " " + quantity + "개 차감 ← " + warehouse);
                        log.info("반품 취소 차감: {} {}개 ← {}", productName, quantity, warehouse);
                    } catch (Exception e) {
                        rollbackMsgs.add(productName + " 차감 실패: " + e.getMessage());
                        log.warn("반품 취소 차감 실패: productId={}, {}", productId, e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warn("stockedItems 파싱 실패: {}", e.getMessage());
            }
        }

        ret.setStatus(ProductReturn.ReturnStatus.CANCELLED);
        returnRepository.save(ret);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("return", new ReturnDTO(ret));
        res.put("rollbackMessage", rollbackMsgs.isEmpty()
            ? "입고 내역 없음 (재고 차감 없음)"
            : String.join(", ", rollbackMsgs));
        return ResponseEntity.ok(res);
    }

    /* ── 통계 ─────────────────────────────────────────── */

    @GetMapping("/stats")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> stats() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("requested",  returnRepository.countByStatus(ProductReturn.ReturnStatus.REQUESTED));
        map.put("inspecting", returnRepository.countByStatus(ProductReturn.ReturnStatus.INSPECTING));
        map.put("completed",  returnRepository.countByStatus(ProductReturn.ReturnStatus.COMPLETED));
        map.put("cancelled",  returnRepository.countByStatus(ProductReturn.ReturnStatus.CANCELLED));
        map.put("total",      returnRepository.count());
        return ResponseEntity.ok(map);
    }
}
