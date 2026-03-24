package com.oms.collector.controller;

import com.oms.collector.entity.Product;
import com.oms.collector.entity.Return;
import com.oms.collector.repository.ReturnRepository;
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

    private final ReturnRepository  returnRepository;
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
        public String inspectResult;  // NORMAL | DEFECTIVE
        public String warehouseCode;  // 입고 창고 (NORMAL 시 필수)
        public String inspectMemo;
        public String productSku;     // 재고 복구용 SKU (옵션)
        public Integer restockQty;    // 재고 복구 수량 (옵션)
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

        public ReturnDTO(Return r) {
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
        }
    }

    /* ── 반품 접수 ────────────────────────────────────── */

    @PostMapping
    @Transactional
    public ResponseEntity<ReturnDTO> create(@RequestBody ReturnCreateRequest req) {
        log.info("반품 접수: orderNo={}, type={}", req.orderNo, req.returnType);

        Return ret = Return.builder()
            .orderNo(req.orderNo)
            .channelName(req.channelName)
            .recipientName(req.recipientName)
            .recipientPhone(req.recipientPhone)
            .productName(req.productName)
            .quantity(req.quantity != null ? req.quantity : 1)
            .returnType(Return.ReturnType.valueOf(req.returnType))
            .returnReason(req.returnReason)
            .returnTrackingNo(req.returnTrackingNo)
            .carrierName(req.carrierName)
            .status(Return.ReturnStatus.REQUESTED)
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
        List<Return> returns;

        if (keyword != null && !keyword.isBlank()) {
            returns = returnRepository.searchByKeyword(keyword.trim());
        } else if (status != null && !status.isBlank() && !status.equals("ALL")) {
            returns = returnRepository.findByStatusOrderByCreatedAtDesc(
                Return.ReturnStatus.valueOf(status)
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

        Return ret = returnRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("반품을 찾을 수 없습니다: " + id));

        Return.InspectResult result = Return.InspectResult.valueOf(req.inspectResult);
        ret.setInspectResult(result);
        ret.setInspectMemo(req.inspectMemo);
        ret.setStatus(Return.ReturnStatus.INSPECTING);

        // 창고 결정: 정상 → 요청된 창고, 불량 → 안양(국내온라인 반품)
        String warehouse = result == Return.InspectResult.DEFECTIVE
            ? "ANYANG"
            : (req.warehouseCode != null ? req.warehouseCode : "ANYANG");
        ret.setWarehouseCode(warehouse);

        // 재고 복구 (SKU 입력 시)
        String stockMsg = "";
        if (req.productSku != null && !req.productSku.isBlank()
            && req.restockQty != null && req.restockQty > 0) {
            try {
                List<Product> found = productRepository.searchProducts(req.productSku);
                Product product = found.stream()
                    .filter(p -> req.productSku.equalsIgnoreCase(p.getSku())
                              || req.productSku.equalsIgnoreCase(p.getBarcode()))
                    .findFirst().orElse(found.isEmpty() ? null : found.get(0));

                if (product != null) {
                    String notes = "반품 입고 (" + ret.getOrderNo() + ") "
                        + (result == Return.InspectResult.DEFECTIVE ? "[불량]" : "[정상]");
                    inventoryService.processInboundWithWarehouse(
                        product.getProductId(), req.restockQty, warehouse, null, notes
                    );
                    stockMsg = product.getProductName() + " " + req.restockQty + "개 " + warehouse + " 입고 완료";
                    log.info("반품 재고 복구: {}", stockMsg);
                }
            } catch (Exception e) {
                log.warn("반품 재고 복구 실패: {}", e.getMessage());
                stockMsg = "재고 복구 실패: " + e.getMessage();
            }
        }

        // 정상 반품이고 재고 처리까지 완료되면 바로 COMPLETED
        if (result == Return.InspectResult.NORMAL && !stockMsg.isBlank() && !stockMsg.contains("실패")) {
            ret.setStatus(Return.ReturnStatus.COMPLETED);
            ret.setCompletedAt(LocalDateTime.now());
        }

        returnRepository.save(ret);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("return", new ReturnDTO(ret));
        res.put("stockMessage", stockMsg.isBlank() ? "재고 처리 없음 (SKU 미입력)" : stockMsg);
        return ResponseEntity.ok(res);
    }

    /* ── 환불/교환 처리 메모 ──────────────────────────── */

    @PutMapping("/{id}/resolve")
    @Transactional
    public ResponseEntity<ReturnDTO> resolve(
        @PathVariable UUID id,
        @RequestBody ResolveRequest req
    ) {
        Return ret = returnRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("반품을 찾을 수 없습니다: " + id));

        ret.setResolutionType(Return.ResolutionType.valueOf(req.resolutionType));
        ret.setRefundAmount(req.refundAmount);
        ret.setExchangeOrderNo(req.exchangeOrderNo);
        ret.setResolutionMemo(req.resolutionMemo);
        ret.setStatus(Return.ReturnStatus.COMPLETED);
        ret.setCompletedAt(LocalDateTime.now());

        return ResponseEntity.ok(new ReturnDTO(returnRepository.save(ret)));
    }

    /* ── 반품 취소 ────────────────────────────────────── */

    @PutMapping("/{id}/cancel")
    @Transactional
    public ResponseEntity<ReturnDTO> cancel(@PathVariable UUID id) {
        Return ret = returnRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("반품을 찾을 수 없습니다: " + id));
        ret.setStatus(Return.ReturnStatus.CANCELLED);
        return ResponseEntity.ok(new ReturnDTO(returnRepository.save(ret)));
    }

    /* ── 통계 ─────────────────────────────────────────── */

    @GetMapping("/stats")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> stats() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("requested",  returnRepository.countByStatus(Return.ReturnStatus.REQUESTED));
        map.put("inspecting", returnRepository.countByStatus(Return.ReturnStatus.INSPECTING));
        map.put("completed",  returnRepository.countByStatus(Return.ReturnStatus.COMPLETED));
        map.put("cancelled",  returnRepository.countByStatus(Return.ReturnStatus.CANCELLED));
        map.put("total",      returnRepository.count());
        return ResponseEntity.ok(map);
    }
}
