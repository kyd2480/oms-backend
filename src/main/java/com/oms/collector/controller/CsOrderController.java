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
 * GET /api/cs/orders?startDate=&endDate=&keyword=&searchType=
 *
 * - 모든 상태(PENDING/CONFIRMED/SHIPPED)의 주문을 검색
 * - 주문번호, 수취인, 연락처, 상품명, 송장번호로 검색 가능
 * - 날짜 범위 기반 조회
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
        public String  address;
        public String  productName;      // 상품명 합본
        public int     quantity;
        public String  orderStatus;      // PENDING / CONFIRMED / SHIPPED
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
        }
    }

    /**
     * CS 주문 검색
     * GET /api/cs/orders?startDate=2026-01-01&endDate=2026-03-26&keyword=OMS-2026&searchType=orderNo
     */
    @GetMapping("/orders")
    @Transactional(readOnly = true)
    public ResponseEntity<List<CsOrderDTO>> search(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false, defaultValue = "all") String searchType
    ) {
        // 날짜 기본값: 오늘 기준 1개월
        LocalDateTime start = (startDate != null ? startDate : LocalDate.now().minusMonths(1))
            .atStartOfDay();
        LocalDateTime end   = (endDate   != null ? endDate   : LocalDate.now())
            .atTime(23, 59, 59);

        List<Order> orders = orderRepository.findByDateRange(start, end);

        // 키워드 필터
        if (keyword != null && !keyword.isBlank()) {
            final String kw = keyword.trim().toLowerCase();
            orders = orders.stream().filter(o -> {
                switch (searchType.toLowerCase()) {
                    case "orderno":
                    case "주문번호":
                        return o.getOrderNo() != null &&
                               o.getOrderNo().toLowerCase().contains(kw);
                    case "recipientname":
                    case "수취인":
                        return o.getRecipientName() != null &&
                               o.getRecipientName().toLowerCase().contains(kw);
                    case "recipientphone":
                    case "연락처":
                        return o.getRecipientPhone() != null &&
                               o.getRecipientPhone().toLowerCase().contains(kw);
                    case "trackingno":
                    case "송장번호":
                        return getTrackingNo(o) != null &&
                               getTrackingNo(o).toLowerCase().contains(kw);
                    case "productname":
                    case "상품명":
                        return getProductName(o) != null &&
                               getProductName(o).toLowerCase().contains(kw);
                    default: // 통합검색
                        return contains(o.getOrderNo(), kw)
                            || contains(o.getRecipientName(), kw)
                            || contains(o.getRecipientPhone(), kw)
                            || contains(getProductName(o), kw)
                            || contains(getTrackingNo(o), kw);
                }
            }).collect(Collectors.toList());
        }

        // 최신순 정렬
        orders.sort((a, b2) -> {
            if (a.getOrderedAt() == null) return 1;
            if (b2.getOrderedAt() == null) return -1;
            return b2.getOrderedAt().compareTo(a.getOrderedAt());
        });

        return ResponseEntity.ok(
            orders.stream().map(this::toDTO).collect(Collectors.toList())
        );
    }

    /* ── 변환 ─────────────────────────────────────────── */

    private CsOrderDTO toDTO(Order o) {
        CsOrderDTO dto = new CsOrderDTO();
        dto.orderNo       = o.getOrderNo();
        // SalesChannel 이름 추출 - getName() 또는 getChannelName() 시도
        if (o.getChannel() != null) {
            String chName = null;
            for (String method : new String[]{"getName","getChannelName","getDisplayName"}) {
                try {
                    java.lang.reflect.Method m = o.getChannel().getClass().getMethod(method);
                    Object val = m.invoke(o.getChannel());
                    if (val instanceof String && !((String)val).isBlank()) {
                        chName = (String) val; break;
                    }
                } catch (Exception ignored) {}
            }
            dto.channelName = chName; // null이면 프론트에서 '-' 표시
        }
        dto.recipientName = o.getRecipientName();
        dto.recipientPhone= o.getRecipientPhone();
        dto.address       = o.getAddress();
        dto.orderStatus   = o.getOrderStatus() != null ? o.getOrderStatus().name() : "PENDING";
        dto.orderedAt     = o.getOrderedAt() != null ? o.getOrderedAt().toString() : null;

        // 상품명 합본
        dto.productName = getProductName(o);
        dto.quantity    = o.getItems() != null
            ? o.getItems().stream().mapToInt(i -> i.getQuantity() != null ? i.getQuantity() : 0).sum()
            : 0;

        // 송장 정보 (deliveryMemo에서 파싱)
        parseDeliveryMemo(o, dto);

        // 아이템 목록
        if (o.getItems() != null) {
            dto.items = o.getItems().stream().map(it -> {
                CsOrderDTO.ItemDTO item = new CsOrderDTO.ItemDTO();
                item.productName = it.getProductName();
                item.optionName  = it.getOptionName();
                item.quantity    = it.getQuantity() != null ? it.getQuantity() : 0;
                return item;
            }).collect(Collectors.toList());
        } else {
            dto.items = new ArrayList<>();
        }

        return dto;
    }

    /**
     * deliveryMemo에서 송장 정보 파싱
     * 형식: "INVOICE:CARRIER:POST:CARRIER_NAME:우체국택배:TRACKING:1234567890:SHIPPED_AT:2026-03-17T..."
     */
    private void parseDeliveryMemo(Order o, CsOrderDTO dto) {
        String memo = o.getDeliveryMemo();
        if (memo == null || !memo.startsWith("INVOICE:")) return;
        try {
            String[] parts = memo.split(":");
            for (int i = 0; i < parts.length - 1; i++) {
                switch (parts[i]) {
                    case "CARRIER"      -> dto.carrierCode  = parts[i+1];
                    case "CARRIER_NAME" -> dto.carrierName  = parts[i+1];
                    case "TRACKING"     -> dto.trackingNo   = parts[i+1];
                    case "SHIPPED_AT"   -> dto.shippedAt    = parts[i+1];
                }
            }
        } catch (Exception ignored) {}
    }

    private String getProductName(Order o) {
        if (o.getItems() == null || o.getItems().isEmpty()) return null;
        return o.getItems().stream()
            .map(OrderItem::getProductName)
            .filter(Objects::nonNull)
            .collect(Collectors.joining(", "));
    }

    private String getTrackingNo(Order o) {
        String memo = o.getDeliveryMemo();
        if (memo == null || !memo.contains("TRACKING:")) return null;
        try {
            String[] parts = memo.split(":");
            for (int i = 0; i < parts.length - 1; i++) {
                if ("TRACKING".equals(parts[i])) return parts[i+1];
            }
        } catch (Exception ignored) {}
        return null;
    }

    private boolean contains(String val, String kw) {
        return val != null && val.toLowerCase().contains(kw);
    }
}
