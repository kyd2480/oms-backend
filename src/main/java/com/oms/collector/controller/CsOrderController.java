package com.oms.collector.controller;

import com.oms.collector.entity.Order;
import com.oms.collector.entity.OrderItem;
import com.oms.collector.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
        public List<ItemDTO> items;

        public static class ItemDTO {
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

        if (isTracking && hasKeyword) {
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
        if (memo == null || !memo.startsWith("INVOICE:")) return;
        try {
            for (String seg : memo.substring("INVOICE:".length()).split("\\|")) {
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
