package com.oms.collector.controller;

import com.oms.collector.entity.Product;
import com.oms.collector.entity.CsMemo;
import com.oms.collector.entity.ProductReturn;
import com.oms.collector.entity.Order;
import com.oms.collector.entity.OrderItem;
import com.oms.collector.entity.SalesChannel;
import com.oms.collector.repository.OrderRepository;
import com.oms.collector.repository.OrderItemRepository;
import com.oms.collector.repository.CsMemoRepository;
import com.oms.collector.repository.ProductReturnRepository;
import com.oms.collector.service.InventoryService;
import com.oms.collector.service.OrderSequenceService;
import com.oms.collector.repository.ProductRepository;
import com.oms.collector.repository.SalesChannelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CsMemoRepository csMemoRepository;
    private final SalesChannelRepository salesChannelRepository;
    private final OrderSequenceService orderSequenceService;

    /* ── DTO ─────────────────────────────────────────── */

    public static class ReturnCreateRequest {
        public String orderNo;
        public String channelName;
        public String recipientName;
        public String recipientPhone;
        public String productName;
        public Integer quantity;
        public String returnType;
        public String returnReason;
        public String returnTrackingNo;
        public String carrierName;
        public List<java.util.Map<String, Object>> items; // productCode + result 포함
        public String receiveResult;        // NORMAL | DEFECTIVE (전체 판정)
        public String receiveWarehouseCode; // 접수 창고 코드
        public String receiveMemo;          // 접수 작업메모
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
            public String  productSku;    // SKU
            public String  productCode;   // 자사 상품코드 (바코드, 가장 정확)
            public String  productName;   // 상품명
            public String  optionName;    // 옵션명
            public Integer quantity;
            public String  itemResult;    // NORMAL | DEFECTIVE
            public String  warehouseCode;
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
        public String  receiveResult;
        public String  receiveWarehouseCode;
        public String  receiveMemo;
        public String  stockedItems;
        public List<java.util.Map<String, Object>> items; // productCode 포함

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
            this.receiveResult        = r.getReceiveResult();
            this.receiveWarehouseCode = r.getReceiveWarehouseCode();
            this.receiveMemo          = r.getReceiveMemo();
            this.stockedItems         = r.getStockedItems();
            // itemsJson 파싱
            if (r.getItemsJson() != null && !r.getItemsJson().isBlank()) {
                try {
                    this.items = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(r.getItemsJson(),
                            new com.fasterxml.jackson.databind.ObjectMapper()
                                .getTypeFactory().constructCollectionType(List.class, java.util.Map.class));
                } catch (Exception e) {
                    this.items = new ArrayList<>();
                }
            } else {
                this.items = new ArrayList<>();
            }
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
            .receiveResult(req.receiveResult)
            .receiveWarehouseCode(req.receiveWarehouseCode)
            .receiveMemo(req.receiveMemo)
            .status(ProductReturn.ReturnStatus.REQUESTED)
            .source("MANUAL")
            .build();

        com.fasterxml.jackson.databind.ObjectMapper mapper =
            new com.fasterxml.jackson.databind.ObjectMapper();

        // items (productCode + result 포함) JSON 저장
        if (req.items != null && !req.items.isEmpty()) {
            try {
                ret.setItemsJson(mapper.writeValueAsString(req.items));
            } catch (Exception e) {
                log.warn("itemsJson 직렬화 실패: {}", e.getMessage());
            }
        }

        returnRepository.save(ret);

        // 접수 판정: 정상 → 국내온라인반품 창고 기록, 불량 → 반품불량 창고 기록 (모두 총재고 불변)
        List<Map<String,Object>> stockedList = new ArrayList<>();
        if (req.items != null) {
            for (Map<String, Object> item : req.items) {
                String itemResult = (String) item.get("result");
                boolean isNormal    = "NORMAL".equals(itemResult);
                boolean isDefective = "DEFECTIVE".equals(itemResult);
                if (!isNormal && !isDefective) continue;

                String productCode = (String) item.get("productCode");
                if (productCode == null || productCode.isBlank()) continue;

                int qty = item.get("quantity") instanceof Number
                    ? ((Number) item.get("quantity")).intValue() : 1;

                List<Product> found = productRepository.searchProducts(productCode);
                Product product = found.stream()
                    .filter(p -> productCode.equalsIgnoreCase(p.getSku())
                              || productCode.equalsIgnoreCase(p.getBarcode()))
                    .findFirst().orElse(found.isEmpty() ? null : found.get(0));

                if (product == null) {
                    throw new IllegalArgumentException(
                        "상품을 찾을 수 없습니다: [" + productCode + "]\n"
                        + "재고 관리에서 바코드 또는 SKU를 확인해주세요.");
                }

                String wh = (String) item.get("warehouseCode");
                if (wh == null || wh.isBlank()) {
                    log.warn("접수 창고 코드 없음 — 입고 스킵: {}", productCode);
                    continue;
                }

                // REQUIRES_NEW — 실패 시 예외 전파, 접수 전체 롤백
                inventoryService.tryAcceptanceInbound(
                    product.getProductId(), qty, wh,
                    "반품 접수 입고 [" + (isNormal ? "정상" : "불량") + "] (" + ret.getOrderNo() + ")"
                );

                // 취소 시 차감을 위해 내역 저장
                Map<String,Object> record = new LinkedHashMap<>();
                record.put("productId",    product.getProductId().toString());
                record.put("productName",  product.getProductName());
                record.put("sku",          product.getSku());
                record.put("quantity",     qty);
                record.put("warehouseCode", wh);
                record.put("itemResult",   itemResult);
                stockedList.add(record);
            }
        }

        if (!stockedList.isEmpty()) {
            try {
                ret.setStockedItems(mapper.writeValueAsString(stockedList));
                returnRepository.save(ret);
            } catch (Exception e) {
                log.warn("stockedItems 저장 실패: {}", e.getMessage());
            }
        }

        if (ret.getReturnType() == ProductReturn.ReturnType.EXCHANGE
            && (ret.getExchangeOrderNo() == null || ret.getExchangeOrderNo().isBlank())) {
            String exchangeOrderNo = createExchangeOrderIfNeeded(ret, null);
            ret.setExchangeOrderNo(exchangeOrderNo);
            ret.setResolutionType(ProductReturn.ResolutionType.EXCHANGE);
            returnRepository.save(ret);
        }

        createAutoCsMemo(
            ret.getOrderNo(),
            "04.반품요청",
            "고객문의",
            ret.getReturnType() == ProductReturn.ReturnType.EXCHANGE ? "교환" : "반품",
            buildCreateMemoContent(ret, req.items),
            "처리중",
            extractWorkerName(req.items)
        );

        return ResponseEntity.ok(new ReturnDTO(ret));
    }

    /* ── 반품 목록 ────────────────────────────────────── */

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<ReturnDTO>> list(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String orderNo,
        @RequestParam(required = false) String startDate,
        @RequestParam(required = false) String endDate
    ) {
        List<ProductReturn> returns;

        if (orderNo != null && !orderNo.isBlank()) {
            returns = returnRepository.findByOrderNoOrderByCreatedAtDesc(orderNo.trim());
        } else if (keyword != null && !keyword.isBlank()) {
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

            // productCode(바코드) → SKU → 상품명 순으로 검색
            String searchKey =
                (item.productCode != null && !item.productCode.isBlank()) ? item.productCode :
                (item.productSku  != null && !item.productSku.isBlank())  ? item.productSku  :
                item.productName;
            if (searchKey == null || searchKey.isBlank()) continue;

            List<Product> found = productRepository.searchProducts(searchKey);
            Product product = found.stream()
                .filter(p -> {
                    if (item.productCode != null && !item.productCode.isBlank()) {
                        return item.productCode.equalsIgnoreCase(p.getSku())
                            || item.productCode.equalsIgnoreCase(p.getBarcode());
                    }
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

            boolean isDefective = "DEFECTIVE".equals(item.itemResult);
            // 정상: ANYANG_KO_RETURN → ANYANG (총재고 증가)
            // 불량: ANYANG_KO_RETURN → RETURN_POOR (총재고 불변)
            String toWarehouse = isDefective ? "RETURN_POOR" : "ANYANG";
            String notes = "반품 검수 이동 (" + ret.getOrderNo() + ") "
                + (isDefective ? "[불량]" : "[정상]");

            inventoryService.warehouseTransfer(
                product.getProductId(), item.quantity,
                "ANYANG_KO_RETURN", toWarehouse,
                !isDefective, notes
            );

            // 취소 시 차감을 위해 도착 창고 기준으로 내역 저장
            Map<String,Object> record = new LinkedHashMap<>();
            record.put("productId",    product.getProductId().toString());
            record.put("productName",  product.getProductName());
            record.put("sku",          product.getSku());
            record.put("quantity",     item.quantity);
            record.put("warehouseCode", toWarehouse);
            record.put("itemResult",   item.itemResult);
            stockedList.add(record);

            stockMsgs.add(product.getProductName() + " " + item.quantity + "개 → " + toWarehouse);
            log.info("반품 검수 이동: {} {}개 → {}", product.getSku(), item.quantity, toWarehouse);
        }

        // 입고 내역 JSON으로 저장
        try {
            ret.setStockedItems(new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(stockedList));
        } catch (Exception ex) {
            log.warn("stockedItems 직렬화 실패: {}", ex.getMessage());
        }

        // 교환은 입고 시 신규 주문 자동 생성
        if (ret.getReturnType() == ProductReturn.ReturnType.EXCHANGE
            && (ret.getExchangeOrderNo() == null || ret.getExchangeOrderNo().isBlank())) {
            String exchangeOrderNo = createExchangeOrderIfNeeded(ret, null);
            ret.setExchangeOrderNo(exchangeOrderNo);
            ret.setResolutionType(ProductReturn.ResolutionType.EXCHANGE);
            ret.setResolutionMemo(req.inspectMemo);
        }

        // 불량 하나라도 → INSPECTING(환불 대기), 전체 정상 → COMPLETED
        boolean hasDefective = items.stream().anyMatch(it -> "DEFECTIVE".equals(it.itemResult));
        if (ret.getReturnType() == ProductReturn.ReturnType.EXCHANGE) {
            ret.setStatus(ProductReturn.ReturnStatus.COMPLETED);
            ret.setCompletedAt(LocalDateTime.now());
        } else if (hasDefective) {
            ret.setStatus(ProductReturn.ReturnStatus.INSPECTING);
        } else {
            ret.setStatus(ProductReturn.ReturnStatus.COMPLETED);
            ret.setCompletedAt(LocalDateTime.now());
        }

        returnRepository.save(ret);
        createAutoCsMemo(
            ret.getOrderNo(),
            "04.반품요청",
            "내부처리",
            ret.getReturnType() == ProductReturn.ReturnType.EXCHANGE ? "교환" : "반품",
            buildInspectMemoContent(ret, req, stockMsgs),
            ret.getStatus() == ProductReturn.ReturnStatus.COMPLETED ? "완료" : "처리중",
            "시스템"
        );

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
        ret.setResolutionMemo(req.resolutionMemo);

        if (ret.getResolutionType() == ProductReturn.ResolutionType.EXCHANGE) {
            String exchangeOrderNo = createExchangeOrderIfNeeded(ret, req.exchangeOrderNo);
            ret.setExchangeOrderNo(exchangeOrderNo);
        } else {
            ret.setExchangeOrderNo(req.exchangeOrderNo);
        }

        ret.setStatus(ProductReturn.ReturnStatus.COMPLETED);
        ret.setCompletedAt(LocalDateTime.now());
        ProductReturn saved = returnRepository.save(ret);
        createAutoCsMemo(
            ret.getOrderNo(),
            "04.반품요청",
            "내부처리",
            ret.getReturnType() == ProductReturn.ReturnType.EXCHANGE ? "교환" : "반품",
            buildResolveMemoContent(ret),
            "완료",
            "시스템"
        );

        return ResponseEntity.ok(new ReturnDTO(saved));
    }

    private String createExchangeOrderIfNeeded(ProductReturn ret, String requestedExchangeOrderNo) {
        if (ret.getExchangeOrderNo() != null && !ret.getExchangeOrderNo().isBlank()) {
            return ret.getExchangeOrderNo();
        }
        if (requestedExchangeOrderNo != null && !requestedExchangeOrderNo.isBlank()) {
            return requestedExchangeOrderNo;
        }

        Order sourceOrder = orderRepository.findWithItemsByOrderNo(ret.getOrderNo()).orElse(null);
        String newOrderNo = orderSequenceService.generateOrderNo();

        Order newOrder = Order.builder()
            .orderNo(newOrderNo)
            .rawOrder(null)
            .channel(resolveChannel(ret, sourceOrder))
            .channelOrderNo(buildExchangeChannelOrderNo(ret.getOrderNo(), newOrderNo))
            .customerName(sourceOrder != null && sourceOrder.getCustomerName() != null ? sourceOrder.getCustomerName() : defaultString(ret.getRecipientName()))
            .customerPhone(sourceOrder != null ? sourceOrder.getCustomerPhone() : ret.getRecipientPhone())
            .customerEmail(sourceOrder != null ? sourceOrder.getCustomerEmail() : null)
            .recipientName(sourceOrder != null ? sourceOrder.getRecipientName() : ret.getRecipientName())
            .recipientPhone(sourceOrder != null ? sourceOrder.getRecipientPhone() : ret.getRecipientPhone())
            .postalCode(sourceOrder != null ? sourceOrder.getPostalCode() : null)
            .address(sourceOrder != null ? sourceOrder.getAddress() : "")
            .addressDetail(sourceOrder != null ? sourceOrder.getAddressDetail() : null)
            .deliveryMemo(buildExchangeDeliveryMemo(sourceOrder, ret))
            .totalAmount(BigDecimal.ZERO)
            .paymentAmount(BigDecimal.ZERO)
            .shippingFee(BigDecimal.ZERO)
            .discountAmount(BigDecimal.ZERO)
            .orderStatus(Order.OrderStatus.PENDING)
            .paymentStatus(Order.PaymentStatus.PAID)
            .orderedAt(LocalDateTime.now())
            .paidAt(LocalDateTime.now())
            .build();

        orderRepository.saveAndFlush(newOrder);

        List<OrderItem> newItems = buildExchangeItems(ret, sourceOrder, newOrder);
        if (newItems.isEmpty()) {
            newItems.add(OrderItem.builder()
                .order(newOrder)
                .productCode("")
                .channelProductCode("")
                .productName(ret.getProductName() != null ? ret.getProductName() : "교환상품")
                .optionName("")
                .quantity(ret.getQuantity() != null ? ret.getQuantity() : 1)
                .cancelledQuantity(0)
                .itemStatus(OrderItem.ItemStatus.ACTIVE)
                .unitPrice(BigDecimal.ZERO)
                .totalPrice(BigDecimal.ZERO)
                .build());
        }

        newItems.forEach(newOrder::addItem);
        orderItemRepository.saveAll(newItems);
        BigDecimal total = newItems.stream()
            .map(item -> item.getTotalPrice() != null ? item.getTotalPrice() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        newOrder.setTotalAmount(total);
        newOrder.setPaymentAmount(total);

        orderRepository.save(newOrder);
        log.info("교환 재출고 주문 생성: returnId={} sourceOrder={} newOrder={}", ret.getReturnId(), ret.getOrderNo(), newOrderNo);
        return newOrderNo;
    }

    private SalesChannel resolveChannel(ProductReturn ret, Order sourceOrder) {
        if (sourceOrder != null && sourceOrder.getChannel() != null) {
            return sourceOrder.getChannel();
        }
        if (ret.getChannelName() == null || ret.getChannelName().isBlank()) {
            return null;
        }
        return salesChannelRepository.findFirstByChannelNameIgnoreCase(ret.getChannelName()).orElse(null);
    }

    private List<OrderItem> buildExchangeItems(ProductReturn ret, Order sourceOrder, Order newOrder) {
        List<Map<String, Object>> requestItems = parseItemsJson(ret.getItemsJson());
        if (requestItems.isEmpty() && sourceOrder == null) {
            return new ArrayList<>();
        }

        List<OrderItem> sourceItems = sourceOrder != null ? sourceOrder.getItems() : List.of();
        List<OrderItem> built = new ArrayList<>();

        for (Map<String, Object> requested : requestItems) {
            String productCode = firstNonBlank(
                asString(requested.get("replacementProductCode")),
                asString(requested.get("replacementBarcode")),
                asString(requested.get("productCode"))
            );
            String productName = firstNonBlank(
                asString(requested.get("replacementProductName")),
                asString(requested.get("productName"))
            );
            String optionName = firstNonBlank(
                asString(requested.get("replacementOptionName")),
                asString(requested.get("optionName"))
            );
            int quantity = requested.get("quantity") instanceof Number
                ? ((Number) requested.get("quantity")).intValue()
                : 1;

            OrderItem sourceItem = sourceItems.stream()
                .filter(item -> matchesExchangeItem(item, productCode, productName, optionName))
                .findFirst()
                .orElse(null);

            BigDecimal unitPrice = sourceItem != null && sourceItem.getUnitPrice() != null
                ? sourceItem.getUnitPrice()
                : BigDecimal.ZERO;

            OrderItem newItem = OrderItem.builder()
                .order(newOrder)
                .productCode(productCode)
                .channelProductCode(productCode)
                .productName(defaultString(productName, sourceItem != null ? sourceItem.getProductName() : null, ret.getProductName(), "교환상품"))
                .optionName(optionName)
                .quantity(quantity)
                .cancelledQuantity(0)
                .itemStatus(OrderItem.ItemStatus.ACTIVE)
                .unitPrice(unitPrice)
                .totalPrice(unitPrice.multiply(BigDecimal.valueOf(quantity)))
                .build();
            built.add(newItem);
        }

        if (built.isEmpty() && sourceOrder != null) {
            for (OrderItem sourceItem : sourceItems) {
                int quantity = sourceItem.getQuantity() != null ? sourceItem.getQuantity() : 1;
                built.add(OrderItem.builder()
                    .order(newOrder)
                    .productCode(sourceItem.getProductCode())
                    .channelProductCode(sourceItem.getChannelProductCode())
                    .productName(sourceItem.getProductName())
                    .optionName(sourceItem.getOptionName())
                    .quantity(quantity)
                    .cancelledQuantity(0)
                    .itemStatus(OrderItem.ItemStatus.ACTIVE)
                    .unitPrice(sourceItem.getUnitPrice() != null ? sourceItem.getUnitPrice() : BigDecimal.ZERO)
                    .totalPrice(sourceItem.getTotalPrice() != null ? sourceItem.getTotalPrice() : BigDecimal.ZERO)
                    .build());
            }
        }

        return built;
    }

    private List<Map<String, Object>> parseItemsJson(String itemsJson) {
        if (itemsJson == null || itemsJson.isBlank()) {
            return List.of();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(
                itemsJson,
                mapper.getTypeFactory().constructCollectionType(List.class, Map.class)
            );
        } catch (Exception e) {
            log.warn("교환 itemsJson 파싱 실패: {}", e.getMessage());
            return List.of();
        }
    }

    private boolean matchesExchangeItem(OrderItem item, String productCode, String productName, String optionName) {
        if (item == null) {
            return false;
        }
        boolean codeMatch = productCode != null && !productCode.isBlank() && (
            productCode.equalsIgnoreCase(defaultString(item.getProductCode()))
                || productCode.equalsIgnoreCase(defaultString(item.getChannelProductCode()))
        );
        boolean nameMatch = productName != null && !productName.isBlank()
            && productName.equalsIgnoreCase(defaultString(item.getProductName()));
        boolean optionMatch = optionName == null || optionName.isBlank()
            || optionName.equalsIgnoreCase(defaultString(item.getOptionName()));
        return (codeMatch || nameMatch) && optionMatch;
    }

    private String buildExchangeChannelOrderNo(String sourceOrderNo, String newOrderNo) {
        return "EXCHANGE-" + defaultString(sourceOrderNo).replace(" ", "") + "-" + newOrderNo;
    }

    private String buildExchangeDeliveryMemo(Order sourceOrder, ProductReturn ret) {
        String originalMemo = sourceOrder != null && sourceOrder.getDeliveryMemo() != null
            ? sourceOrder.getDeliveryMemo()
            : "";
        return "교환 재출고 / 원주문:" + ret.getOrderNo()
            + (ret.getReturnReason() != null && !ret.getReturnReason().isBlank() ? " / 사유:" + ret.getReturnReason() : "")
            + (originalMemo.isBlank() ? "" : " / 원메모참고");
    }

    private String asString(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private String defaultString(String value) {
        return value != null ? value : "";
    }

    private String defaultString(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) return first;
        if (second != null && !second.isBlank()) return second;
        return fallback;
    }

    private String defaultString(String first, String second, String third, String fallback) {
        if (first != null && !first.isBlank()) return first;
        if (second != null && !second.isBlank()) return second;
        if (third != null && !third.isBlank()) return third;
        return fallback;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private void createAutoCsMemo(
        String orderNo,
        String csType,
        String csDept,
        String csKind,
        String content,
        String status,
        String writer
    ) {
        if (orderNo == null || orderNo.isBlank() || content == null || content.isBlank()) {
            return;
        }
        csMemoRepository.save(CsMemo.builder()
            .orderNo(orderNo)
            .csType(csType)
            .csDept(csDept)
            .csKind(csKind)
            .content(content)
            .status(status)
            .writer(writer != null && !writer.isBlank() ? writer : "시스템")
            .build());
    }

    private String extractWorkerName(List<Map<String, Object>> items) {
        if (items == null) {
            return "시스템";
        }
        for (Map<String, Object> item : items) {
            String workerId = asString(item.get("workerId"));
            if (!workerId.isBlank()) {
                return workerId;
            }
        }
        return "시스템";
    }

    private String buildCreateMemoContent(ProductReturn ret, List<Map<String, Object>> items) {
        List<String> lines = new ArrayList<>();
        lines.add(ret.getReturnType() == ProductReturn.ReturnType.EXCHANGE ? "[교환 접수]" : "[반품 접수]");
        if (ret.getReturnTrackingNo() != null && !ret.getReturnTrackingNo().isBlank()) {
            lines.add("반송장번호: " + ret.getReturnTrackingNo());
        }
        if (ret.getCarrierName() != null && !ret.getCarrierName().isBlank()) {
            lines.add("택배사: " + ret.getCarrierName());
        }
        if (ret.getReturnReason() != null && !ret.getReturnReason().isBlank()) {
            lines.add("사유: " + ret.getReturnReason());
        }
        if (ret.getReturnType() == ProductReturn.ReturnType.EXCHANGE
            && ret.getExchangeOrderNo() != null
            && !ret.getExchangeOrderNo().isBlank()) {
            lines.add("생성주문번호: " + ret.getExchangeOrderNo());
        }
        if (items != null && !items.isEmpty()) {
            lines.add("대상상품:");
            for (Map<String, Object> item : items) {
                String productName = asString(item.get("productName"));
                String optionName = asString(item.get("optionName"));
                String replacementProductName = asString(item.get("replacementProductName"));
                String replacementOptionName = asString(item.get("replacementOptionName"));
                int quantity = item.get("quantity") instanceof Number ? ((Number) item.get("quantity")).intValue() : 1;
                String line = "- " + productName
                    + (optionName.isBlank() ? "" : " / " + optionName)
                    + " x " + quantity;
                if (ret.getReturnType() == ProductReturn.ReturnType.EXCHANGE && !replacementProductName.isBlank()) {
                    line += " -> " + replacementProductName
                        + (replacementOptionName.isBlank() ? "" : " / " + replacementOptionName);
                }
                lines.add(line);
            }
        }
        return String.join("\n", lines);
    }

    private String buildInspectMemoContent(ProductReturn ret, InspectRequest req, List<String> stockMsgs) {
        List<String> lines = new ArrayList<>();
        lines.add(ret.getReturnType() == ProductReturn.ReturnType.EXCHANGE ? "[교환 입고 처리]" : "[반품 입고 처리]");
        if (req.inspectResult != null && !req.inspectResult.isBlank()) {
            lines.add("검수결과: " + req.inspectResult);
        }
        if (stockMsgs != null && !stockMsgs.isEmpty()) {
            lines.add("재고처리: " + String.join(", ", stockMsgs));
        }
        if (ret.getExchangeOrderNo() != null && !ret.getExchangeOrderNo().isBlank()) {
            lines.add("생성주문번호: " + ret.getExchangeOrderNo());
        }
        if (req.inspectMemo != null && !req.inspectMemo.isBlank()) {
            lines.add("메모: " + req.inspectMemo);
        }
        return String.join("\n", lines);
    }

    private String buildResolveMemoContent(ProductReturn ret) {
        List<String> lines = new ArrayList<>();
        lines.add(ret.getReturnType() == ProductReturn.ReturnType.EXCHANGE ? "[교환 처리 완료]" : "[반품 처리 완료]");
        if (ret.getResolutionType() != null) {
            lines.add("처리유형: " + ret.getResolutionType().name());
        }
        if (ret.getRefundAmount() != null) {
            lines.add("환불금액: " + ret.getRefundAmount());
        }
        if (ret.getExchangeOrderNo() != null && !ret.getExchangeOrderNo().isBlank()) {
            lines.add("교환주문번호: " + ret.getExchangeOrderNo());
        }
        if (ret.getResolutionMemo() != null && !ret.getResolutionMemo().isBlank()) {
            lines.add("메모: " + ret.getResolutionMemo());
        }
        return String.join("\n", lines);
    }

    private String buildCancelMemoContent(ProductReturn ret, List<String> rollbackMsgs) {
        List<String> lines = new ArrayList<>();
        lines.add(ret.getReturnType() == ProductReturn.ReturnType.EXCHANGE ? "[교환 취소]" : "[반품 취소]");
        if (rollbackMsgs != null && !rollbackMsgs.isEmpty()) {
            lines.add("차감처리: " + String.join(", ", rollbackMsgs));
        }
        return String.join("\n", lines);
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
                    } catch (IllegalStateException e) {
                        // 재고 부족이어도 총재고/창고재고 강제 차감 (반품 취소이므로)
                        log.warn("반품 취소 재고 부족 → 강제 차감: {} {}개 ← {}", productName, quantity, warehouse);
                        inventoryService.forceOutboundForReturn(
                            productId, quantity, warehouse,
                            "반품 취소 강제 차감 (" + ret.getOrderNo() + ")"
                        );
                        rollbackMsgs.add(productName + " " + quantity + "개 강제 차감 ← " + warehouse);
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
        createAutoCsMemo(
            ret.getOrderNo(),
            "04.반품요청",
            "내부처리",
            ret.getReturnType() == ProductReturn.ReturnType.EXCHANGE ? "교환" : "반품",
            buildCancelMemoContent(ret, rollbackMsgs),
            "완료",
            "시스템"
        );

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
