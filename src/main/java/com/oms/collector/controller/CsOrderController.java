package com.oms.collector.controller;

import com.oms.collector.entity.Order;
import com.oms.collector.entity.OrderItem;
import com.oms.collector.repository.OrderItemRepository;
import com.oms.collector.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CS 관리 전용 주문 검색 API
 *
 * GET /api/cs/orders
 *   ?startDate=2026-01-01   날짜 범위 시작
 *   &endDate=2026-03-26     날짜 범위 끝
 *   &dateType=ordered       ordered(주문일자) | shipped(발송일자)
 *   &searchType=주문번호     통합검색|주문번호|수취인|연락처|송장번호|상품명
 *   &keyword=OMS-2026       검색어
 */
@Slf4j
@RestController
@RequestMapping("/api/cs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CsOrderController {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public static class CsOrderDTO {
        public String  orderNo;
        public String  channelName;
        public String  recipientName;
        public String  recipientPhone;
        public String  postalCode;
        public String  address;
        public String  addressDetail;
        public String  productName;
        public int     quantity;
        public String  orderStatus;
        public String  orderedAt;
        public String  shippedAt;
        public String  carrierCode;
        public String  carrierName;
        public String  trackingNo;
        public Boolean inspectionCompleted;
        public Boolean shippingHold;
        public String  holdReason;
        public Boolean priorityAllocation;
        public Boolean allocationExcluded;
        public String  printTypeCode;
        public String  printTypeName;
        public String  mergedIntoOrderNo;
        public String  splitFromOrderNo;
        public List<ItemDTO> items;

        public static class ItemDTO {
            public String itemId;
            public String productName;
            public String optionName;
            public int    quantity;
            public String productCode;  // 자사 상품 코드 (바코드/SKU 역할)
            public int    cancelledQuantity;
            public String itemStatus;
        }
    }

    public static class UpdateShippingRequest {
        public String recipientName;
        public String recipientPhone;
        public String postalCode;
        public String address;
        public String addressDetail;
    }

    public static class ReasonRequest {
        public String reason;
    }

    public static class PrintTypeRequest {
        public String printTypeCode;
        public String printTypeName;
    }

    public static class MergeRequest {
        public String targetOrderNo;
        public List<String> sourceOrderNos;
    }

    public static class SplitRequest {
        public List<String> itemIds;
    }

    @GetMapping("/orders")
    @Transactional(readOnly = true)
    public ResponseEntity<List<CsOrderDTO>> search(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
        @RequestParam(required = false, defaultValue = "ordered") String dateType,
        @RequestParam(required = false, defaultValue = "통합검색") String searchType,
        @RequestParam(required = false) String keyword
    ) {
        LocalDateTime start = (startDate != null ? startDate : LocalDate.now().minusWeeks(1)).atStartOfDay();
        LocalDateTime end   = (endDate   != null ? endDate   : LocalDate.now()).atTime(23, 59, 59);

        boolean isShippedDate = "shipped".equalsIgnoreCase(dateType) || "발송일자".equals(dateType);
        boolean isTracking    = "송장번호".equals(searchType) || "trackingno".equalsIgnoreCase(searchType);
        boolean hasKeyword    = keyword != null && !keyword.isBlank();
        final String kw       = hasKeyword ? keyword.trim().toLowerCase() : "";

        List<Order> orders;

        boolean directOrderSearch = hasKeyword && ("주문번호".equals(searchType) || "송장번호".equals(searchType));
        if (directOrderSearch) {
            // CS에서 주문번호/송장번호를 찍어 찾을 때는 기간 제한을 걸지 않는다.
            orders = orderRepository.searchByOrderNoOrTracking(keyword.trim());
        } else if (isTracking && hasKeyword) {
            if (isShippedDate) {
                // 발송일자 기준 + 송장번호 검색
                orders = orderRepository.findShippedByDateRange(start, end).stream()
                    .filter(o -> contains(getTrackingNo(o), kw))
                    .collect(Collectors.toList());
            } else {
                // 주문일자 기준 + 송장번호 검색 (DB LIKE 쿼리)
                orders = orderRepository.findByDateRangeAndTracking(start, end, keyword.trim());
            }
        } else if (isShippedDate) {
            // 발송일자 기준 (SHIPPED 주문의 updatedAt)
            orders = orderRepository.findShippedByDateRange(start, end);
            if (hasKeyword) orders = filterByKeyword(orders, searchType, kw);
        } else {
            // 주문일자 기준 (기본)
            orders = orderRepository.findByDateRange(start, end);
            if (hasKeyword) orders = filterByKeyword(orders, searchType, kw);
        }

        // 정렬
        orders.sort((a, b) -> {
            LocalDateTime da = isShippedDate ? a.getUpdatedAt() : a.getOrderedAt();
            LocalDateTime db = isShippedDate ? b.getUpdatedAt() : b.getOrderedAt();
            if (da == null) return 1;
            if (db == null) return -1;
            return db.compareTo(da);
        });

        log.info("CS 주문 검색: dateType={}, searchType={}, keyword={}, 결과={}건",
            dateType, searchType, keyword, orders.size());

        return ResponseEntity.ok(orders.stream().map(this::toDTO).collect(Collectors.toList()));
    }

    @PatchMapping("/orders/{orderNo}/shipping")
    @Transactional
    public ResponseEntity<?> updateShippingInfo(
        @PathVariable String orderNo,
        @RequestBody UpdateShippingRequest request
    ) {
        Order order = orderRepository.findByOrderNo(orderNo)
            .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + orderNo));

        String recipientName = normalizeText(request.recipientName);
        String recipientPhone = normalizePhone(request.recipientPhone);
        String postalCode = normalizePostalCode(request.postalCode);
        String address = normalizeText(request.address);
        String addressDetail = normalizeText(request.addressDetail);

        if (recipientName == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "수령인명을 입력해주세요."));
        }
        if (recipientPhone == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "수령인 연락처를 입력해주세요."));
        }
        if (postalCode == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "우편번호는 5자리 숫자로 입력해주세요."));
        }
        if (address == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "주소를 입력해주세요."));
        }

        order.setRecipientName(recipientName);
        order.setRecipientPhone(recipientPhone);
        order.setPostalCode(postalCode);
        order.setAddress(address);
        order.setAddressDetail(addressDetail);
        orderRepository.save(order);

        log.info("CS 배송지 수정: orderNo={}, recipientName={}, postalCode={}", orderNo, recipientName, postalCode);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "배송지 정보가 수정되었습니다.",
            "order", toDTO(order)
        ));
    }

    @PatchMapping("/orders/{orderNo}/hold")
    @Transactional
    public ResponseEntity<?> setHold(@PathVariable String orderNo, @RequestBody ReasonRequest request) {
        String reason = normalizeText(request != null ? request.reason : null);
        if (reason == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "보류 사유는 필수입니다."));
        }
        Order order = getOrder(orderNo);
        order.setShippingHold(true);
        order.setHoldReason(reason);
        orderRepository.save(order);
        return ResponseEntity.ok(Map.of("success", true, "message", "보류 설정 완료", "order", toDTO(order)));
    }

    @PatchMapping("/orders/{orderNo}/hold-release")
    @Transactional
    public ResponseEntity<?> releaseHold(@PathVariable String orderNo) {
        Order order = getOrder(orderNo);
        order.setShippingHold(false);
        order.setHoldReason(null);
        orderRepository.save(order);
        return ResponseEntity.ok(Map.of("success", true, "message", "보류 해지 완료", "order", toDTO(order)));
    }

    @PatchMapping("/orders/{orderNo}/priority")
    @Transactional
    public ResponseEntity<?> setPriority(@PathVariable String orderNo) {
        Order order = getOrder(orderNo);
        order.setPriorityAllocation(true);
        orderRepository.save(order);
        return ResponseEntity.ok(Map.of("success", true, "message", "우선할당 설정 완료", "order", toDTO(order)));
    }

    @PatchMapping("/orders/{orderNo}/priority-release")
    @Transactional
    public ResponseEntity<?> releasePriority(@PathVariable String orderNo) {
        Order order = getOrder(orderNo);
        order.setPriorityAllocation(false);
        orderRepository.save(order);
        return ResponseEntity.ok(Map.of("success", true, "message", "우선할당 취소 완료", "order", toDTO(order)));
    }

    @PatchMapping("/orders/{orderNo}/allocation-exclude")
    @Transactional
    public ResponseEntity<?> excludeAllocation(@PathVariable String orderNo, @RequestBody(required = false) ReasonRequest request) {
        Order order = getOrder(orderNo);
        order.setAllocationExcluded(true);
        orderRepository.save(order);
        return ResponseEntity.ok(Map.of("success", true, "message", "재고할당 제외 완료", "order", toDTO(order)));
    }

    @PatchMapping("/orders/{orderNo}/allocation-exclude-release")
    @Transactional
    public ResponseEntity<?> releaseAllocationExclude(@PathVariable String orderNo) {
        Order order = getOrder(orderNo);
        order.setAllocationExcluded(false);
        orderRepository.save(order);
        return ResponseEntity.ok(Map.of("success", true, "message", "할당제외 취소 완료", "order", toDTO(order)));
    }

    @PatchMapping("/orders/{orderNo}/print-type")
    @Transactional
    public ResponseEntity<?> setPrintType(@PathVariable String orderNo, @RequestBody PrintTypeRequest request) {
        Order order = getOrder(orderNo);
        String code = normalizeText(request != null ? request.printTypeCode : null);
        String name = normalizeText(request != null ? request.printTypeName : null);
        if (code == null || name == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "인쇄구분을 선택해주세요."));
        }
        order.setPrintTypeCode(code);
        order.setPrintTypeName(name);
        orderRepository.save(order);
        return ResponseEntity.ok(Map.of("success", true, "message", "인쇄구분 변경 완료", "order", toDTO(order)));
    }

    @PostMapping("/orders/merge")
    @Transactional
    public ResponseEntity<?> mergeOrders(@RequestBody MergeRequest request) {
        if (request == null || normalizeText(request.targetOrderNo) == null || request.sourceOrderNos == null || request.sourceOrderNos.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "합포 대상 주문을 선택해주세요."));
        }
        Order target = getOrder(request.targetOrderNo);
        ensureNoInvoice(target, "합포");
        int moved = 0;
        for (String sourceNo : request.sourceOrderNos) {
            if (sourceNo == null || sourceNo.equals(target.getOrderNo())) continue;
            Order source = getOrder(sourceNo);
            ensureNoInvoice(source, "합포");
            source.getItems().size();
            List<OrderItem> moving = new ArrayList<>(source.getItems());
            for (OrderItem item : moving) {
                source.removeItem(item);
                target.addItem(item);
                moved++;
            }
            source.setOrderStatus(Order.OrderStatus.CANCELLED);
            source.setMergedIntoOrderNo(target.getOrderNo());
            source.setAllocationExcluded(true);
        }
        orderRepository.save(target);
        return ResponseEntity.ok(Map.of("success", true, "message", moved + "개 상품 합포 완료", "order", toDTO(target)));
    }

    @PostMapping("/orders/{orderNo}/split")
    @Transactional
    public ResponseEntity<?> splitOrder(@PathVariable String orderNo, @RequestBody SplitRequest request) {
        if (request == null || request.itemIds == null || request.itemIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "나눌 상품을 선택해주세요."));
        }
        Order source = getOrder(orderNo);
        ensureNoInvoice(source, "나누기");
        source.getItems().size();
        Set<UUID> selectedIds = request.itemIds.stream()
            .map(id -> {
                try { return UUID.fromString(id); }
                catch (Exception e) { return null; }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        long selectedCount = source.getItems().stream()
            .filter(item -> selectedIds.contains(item.getItemId()))
            .count();
        if (selectedCount == 0 || selectedCount >= source.getItems().size()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "일부 상품만 선택해야 주문을 나눌 수 있습니다."));
        }
        try {
            // 1. split 주문 먼저 저장 (orderId 확보)
            Order split = cloneOrderHeader(source, nextSplitOrderNo(source.getOrderNo()));
            split.setSplitFromOrderNo(source.getOrderNo());
            orderRepository.saveAndFlush(split);

            // 2. 선택한 상품을 split 주문으로 직접 이동
            List<OrderItem> movingItems = orderItemRepository.findAllById(selectedIds).stream()
                .filter(item -> item.getOrder() != null && source.getOrderId().equals(item.getOrder().getOrderId()))
                .collect(Collectors.toList());
            if (movingItems.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "이동할 상품을 찾지 못했습니다."));
            }
            for (OrderItem item : movingItems) {
                item.setOrder(split);
            }
            orderItemRepository.saveAll(movingItems);

            // 3. 양쪽 주문 재조회 후 금액 재계산
            Order freshSource = getOrder(orderNo);
            Order freshSplit  = getOrder(split.getOrderNo());
            recalcAmount(freshSource);
            recalcAmount(freshSplit);
            orderRepository.save(freshSource);
            orderRepository.save(freshSplit);

            return ResponseEntity.ok(Map.of("success", true, "message", "주문 나누기 완료",
                "order", toDTO(freshSource), "newOrder", toDTO(freshSplit)));
        } catch (Exception e) {
            log.error("나누기 실패: orderNo={}, error={}", orderNo, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()
            ));
        }
    }

    private List<Order> filterByKeyword(List<Order> orders, String searchType, String kw) {
        return orders.stream().filter(o -> switch (searchType) {
            case "주문번호" -> contains(o.getOrderNo(), kw);
            case "수취인"   -> contains(o.getRecipientName(), kw);
            case "연락처"   -> contains(o.getRecipientPhone(), kw);
            case "상품명"   -> contains(getProductName(o), kw);
            case "송장번호" -> contains(getTrackingNo(o), kw);
            default         -> contains(o.getOrderNo(), kw)
                            || contains(o.getRecipientName(), kw)
                            || contains(o.getRecipientPhone(), kw)
                            || contains(getProductName(o), kw)
                            || contains(getTrackingNo(o), kw);
        }).collect(Collectors.toList());
    }

    private CsOrderDTO toDTO(Order o) {
        CsOrderDTO dto = new CsOrderDTO();
        dto.orderNo        = o.getOrderNo();
        dto.recipientName  = o.getRecipientName();
        dto.recipientPhone = o.getRecipientPhone();
        dto.postalCode     = o.getPostalCode();
        dto.address        = o.getAddress();
        dto.addressDetail  = o.getAddressDetail();
        dto.orderStatus    = o.getOrderStatus() != null ? o.getOrderStatus().name() : "PENDING";
        dto.orderedAt      = o.getOrderedAt() != null ? o.getOrderedAt().toString() : null;
        dto.shippedAt      = o.getUpdatedAt() != null ? o.getUpdatedAt().toString() : null;
        dto.shippingHold = Boolean.TRUE.equals(o.getShippingHold());
        dto.inspectionCompleted = Boolean.TRUE.equals(o.getInspectionCompleted());
        dto.holdReason = o.getHoldReason();
        dto.priorityAllocation = Boolean.TRUE.equals(o.getPriorityAllocation());
        dto.allocationExcluded = Boolean.TRUE.equals(o.getAllocationExcluded());
        dto.printTypeCode = o.getPrintTypeCode() != null ? o.getPrintTypeCode() : "NORMAL";
        dto.printTypeName = o.getPrintTypeName() != null ? o.getPrintTypeName() : "일반건";
        dto.mergedIntoOrderNo = o.getMergedIntoOrderNo();
        dto.splitFromOrderNo = o.getSplitFromOrderNo();

        // SalesChannel 이름 추출
        if (o.getChannel() != null) {
            for (String m : new String[]{"getName","getChannelName","getDisplayName"}) {
                try {
                    Object val = o.getChannel().getClass().getMethod(m).invoke(o.getChannel());
                    if (val instanceof String s && !s.isBlank()) { dto.channelName = s; break; }
                } catch (Exception ignored) {}
            }
        }

        dto.productName = getProductName(o);
        dto.quantity    = o.getItems() != null
            ? o.getItems().stream().mapToInt(OrderItem::getActiveQuantity).sum() : 0;

        parseDeliveryMemo(o, dto);

        dto.items = o.getItems() != null
            ? o.getItems().stream().map(it -> {
                CsOrderDTO.ItemDTO item = new CsOrderDTO.ItemDTO();
                item.itemId = it.getItemId() != null ? it.getItemId().toString() : "";
                item.productName = it.getProductName();
                item.optionName  = it.getOptionName();
                item.quantity    = it.getActiveQuantity();
                item.productCode = it.getProductCode();
                item.cancelledQuantity = it.getCancelledQuantity() != null ? it.getCancelledQuantity() : 0;
                item.itemStatus = it.getItemStatus() != null ? it.getItemStatus().name() : "ACTIVE";
                return item;
              }).collect(Collectors.toList())
            : new ArrayList<>();

        return dto;
    }

    // 형식: "INVOICE:CARRIER:CJ|CARRIER_NAME:CJ대한통운|TRACKING:1234567890"
    private void parseDeliveryMemo(Order o, CsOrderDTO dto) {
        String memo = o.getDeliveryMemo();
        if (memo == null || !memo.contains("INVOICE:")) return;
        try {
            String body = memo.substring(memo.indexOf("INVOICE:") + "INVOICE:".length());
            for (String seg : body.split("\\|")) {
                String[] kv = seg.split(":", 2);
                if (kv.length < 2) continue;
                switch (kv[0].trim()) {
                    case "CARRIER"      -> dto.carrierCode = kv[1].trim();
                    case "CARRIER_NAME" -> dto.carrierName = kv[1].trim();
                    case "TRACKING"     -> dto.trackingNo  = kv[1].trim();
                }
            }
        } catch (Exception ignored) {}
    }

    private String getTrackingNo(Order o) {
        String memo = o.getDeliveryMemo();
        if (memo == null || !memo.contains("TRACKING:")) return null;
        try {
            String body = memo.startsWith("INVOICE:") ? memo.substring("INVOICE:".length()) : memo;
            for (String seg : body.split("\\|")) {
                String[] kv = seg.split(":", 2);
                if (kv.length == 2 && "TRACKING".equals(kv[0].trim())) return kv[1].trim();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Order getOrder(String orderNo) {
        return orderRepository.findWithItemsByOrderNo(orderNo)
            .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + orderNo));
    }

    private boolean hasInvoice(Order order) {
        return getTrackingNo(order) != null;
    }

    private void ensureNoInvoice(Order order, String actionName) {
        if (hasInvoice(order)) {
            throw new IllegalStateException(actionName + " 전 송장삭제가 필요합니다: " + order.getOrderNo());
        }
    }

    private Order cloneOrderHeader(Order source, String orderNo) {
        String customerName = source.getCustomerName() != null
            ? source.getCustomerName() : source.getRecipientName();
        String recipientPhone = source.getRecipientPhone() != null
            ? source.getRecipientPhone() : "";
        return Order.builder()
            .orderNo(orderNo)
            .channel(source.getChannel())
            .channelOrderNo(source.getChannelOrderNo())
            .customerName(customerName)
            .customerPhone(source.getCustomerPhone())
            .customerEmail(source.getCustomerEmail())
            .recipientName(source.getRecipientName())
            .recipientPhone(recipientPhone)
            .postalCode(source.getPostalCode())
            .address(source.getAddress())
            .addressDetail(source.getAddressDetail())
            .deliveryMemo(null)
            .totalAmount(BigDecimal.ZERO)
            .paymentAmount(BigDecimal.ZERO)
            .shippingFee(source.getShippingFee() != null ? source.getShippingFee() : BigDecimal.ZERO)
            .discountAmount(BigDecimal.ZERO)
            .orderStatus(source.getOrderStatus())
            .paymentStatus(source.getPaymentStatus())
            .orderedAt(source.getOrderedAt())
            .paidAt(source.getPaidAt())
            .shippingHold(Boolean.TRUE.equals(source.getShippingHold()))
            .holdReason(source.getHoldReason())
            .priorityAllocation(Boolean.TRUE.equals(source.getPriorityAllocation()))
            .allocationExcluded(Boolean.TRUE.equals(source.getAllocationExcluded()))
            .printTypeCode(source.getPrintTypeCode() != null ? source.getPrintTypeCode() : "NORMAL")
            .printTypeName(source.getPrintTypeName() != null ? source.getPrintTypeName() : "일반건")
            .build();
    }

    private String nextSplitOrderNo(String orderNo) {
        String base = orderNo + "-S";
        int idx = 1;
        while (orderRepository.findByOrderNo(base + idx).isPresent()) {
            idx++;
        }
        return base + idx;
    }

    private void recalcAmount(Order order) {
        BigDecimal total = order.getItems().stream()
            .map(item -> item.getTotalPrice() != null ? item.getTotalPrice() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalAmount(total);
        order.setPaymentAmount(total);
    }

    private String getProductName(Order o) {
        if (o.getItems() == null || o.getItems().isEmpty()) return null;
        return o.getItems().stream()
            .filter(item -> item.getActiveQuantity() > 0)
            .map(OrderItem::getProductName).filter(Objects::nonNull)
            .collect(Collectors.joining(", "));
    }

    private boolean contains(String val, String kw) {
        return val != null && val.toLowerCase().contains(kw);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizePhone(String value) {
        String trimmed = normalizeText(value);
        if (trimmed == null) {
            return null;
        }
        String digits = trimmed.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? null : digits;
    }

    private String normalizePostalCode(String value) {
        String trimmed = normalizeText(value);
        if (trimmed == null) {
            return null;
        }
        String digits = trimmed.replaceAll("[^0-9]", "");
        return digits.matches("\\d{5}") ? digits : null;
    }
}
