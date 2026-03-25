package com.oms.collector.controller;

import com.oms.collector.entity.Order;
import com.oms.collector.entity.OrderItem;
import com.oms.collector.entity.Product;
import com.oms.collector.repository.OrderRepository;
import com.oms.collector.repository.ProductRepository;
import com.oms.collector.service.InventoryService;
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
 * 배송전 취소 관리 API
 *
 * GET  /api/cancel/pending          - 취소 대기 주문 (PENDING/CONFIRMED)
 * GET  /api/cancel/orders           - 취소 완료 주문 목록 (CANCELLED)
 * POST /api/cancel/orders/{orderNo} - 주문 취소 처리 (PENDING/CONFIRMED → CANCELLED)
 */
@Slf4j
@RestController
@RequestMapping("/api/cancel")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CancelController {

    private final OrderRepository    orderRepository;
    private final ProductRepository  productRepository;
    private final InventoryService   inventoryService;

    /* ── DTO ─────────────────────────────────────────── */
    public static class CancelOrderDTO {
        public String  orderNo;
        public String  channelName;
        public String  recipientName;
        public String  recipientPhone;
        public String  address;
        public String  productName;
        public int     quantity;
        public String  orderStatus;   // PENDING / CONFIRMED / CANCELLED
        public String  orderedAt;
        public String  cancelledAt;   // updatedAt
        public List<ItemDTO> items;

        public static class ItemDTO {
            public String productName;
            public String optionName;
            public int    quantity;
        }
    }

    /* ── 취소 대기 주문 (PENDING + CONFIRMED) ─────────── */
    @GetMapping("/pending")
    @Transactional(readOnly = true)
    public ResponseEntity<List<CancelOrderDTO>> pending(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
        @RequestParam(required = false) String keyword
    ) {
        LocalDateTime start = (startDate != null ? startDate : LocalDate.now().minusWeeks(1)).atStartOfDay();
        LocalDateTime end   = (endDate   != null ? endDate   : LocalDate.now()).atTime(23, 59, 59);

        List<Order> orders = new ArrayList<>();
        orders.addAll(orderRepository.findByOrderStatusAndDateRange(Order.OrderStatus.PENDING,   start, end));
        orders.addAll(orderRepository.findByOrderStatusAndDateRange(Order.OrderStatus.CONFIRMED, start, end));

        if (keyword != null && !keyword.isBlank()) {
            final String kw = keyword.trim().toLowerCase();
            orders = orders.stream().filter(o ->
                contains(o.getOrderNo(), kw)
                || contains(o.getRecipientName(), kw)
                || contains(o.getRecipientPhone(), kw)
                || contains(getProductName(o), kw)
            ).collect(Collectors.toList());
        }

        orders.sort((a, b) -> {
            if (a.getOrderedAt() == null) return 1;
            if (b.getOrderedAt() == null) return -1;
            return b.getOrderedAt().compareTo(a.getOrderedAt());
        });

        return ResponseEntity.ok(orders.stream().map(this::toDTO).collect(Collectors.toList()));
    }

    @GetMapping("/orders")
    @Transactional(readOnly = true)
    public ResponseEntity<List<CancelOrderDTO>> list(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
        @RequestParam(required = false) String keyword
    ) {
        LocalDateTime start = (startDate != null ? startDate : LocalDate.now().minusWeeks(1)).atStartOfDay();
        LocalDateTime end   = (endDate   != null ? endDate   : LocalDate.now()).atTime(23, 59, 59);

        List<Order> orders = orderRepository.findCancelledByDateRange(start, end);

        if (keyword != null && !keyword.isBlank()) {
            final String kw = keyword.trim().toLowerCase();
            orders = orders.stream().filter(o ->
                contains(o.getOrderNo(), kw)
                || contains(o.getRecipientName(), kw)
                || contains(o.getRecipientPhone(), kw)
                || contains(getProductName(o), kw)
            ).collect(Collectors.toList());
        }

        return ResponseEntity.ok(orders.stream().map(this::toDTO).collect(Collectors.toList()));
    }

    /* ── 주문 취소 처리 ─────────────────────────────── */
    @PostMapping("/orders/{orderNo}")
    @Transactional
    public ResponseEntity<Map<String, Object>> cancel(
        @PathVariable String orderNo,
        @RequestBody(required = false) Map<String, String> body
    ) {
        Order order = orderRepository.findByOrderNo(orderNo)
            .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다: " + orderNo));

        // 이미 발송된 주문은 취소 불가
        if (order.getOrderStatus() == Order.OrderStatus.SHIPPED) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "발송 완료된 주문은 배송전 취소가 불가합니다."
            ));
        }
        if (order.getOrderStatus() == Order.OrderStatus.CANCELLED) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "이미 취소된 주문입니다."
            ));
        }

        // CONFIRMED 상태면 예약 재고 해제
        if (order.getOrderStatus() == Order.OrderStatus.CONFIRMED) {
            order.getItems().forEach(item -> {
                Product product = findProduct(item);
                if (product == null) return;
                int qty = item.getQuantity() != null ? item.getQuantity() : 0;
                try {
                    inventoryService.releaseReservedStock(product.getProductId(), qty);
                    log.info("예약 재고 해제: {} {}개", product.getProductName(), qty);
                } catch (Exception e) {
                    log.warn("예약 해제 실패 (무시): {} - {}", orderNo, e.getMessage());
                }
            });
        }

        order.setOrderStatus(Order.OrderStatus.CANCELLED);
        orderRepository.save(order);

        log.info("주문 취소: {} (이전 상태: {})", orderNo, order.getOrderStatus());

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "취소 완료: " + orderNo
        ));
    }

    /* ── 변환 ────────────────────────────────────────── */
    private CancelOrderDTO toDTO(Order o) {
        CancelOrderDTO dto = new CancelOrderDTO();
        dto.orderNo        = o.getOrderNo();
        dto.recipientName  = o.getRecipientName();
        dto.recipientPhone = o.getRecipientPhone();
        dto.address        = o.getAddress();
        dto.orderStatus    = o.getOrderStatus() != null ? o.getOrderStatus().name() : "PENDING";
        dto.orderedAt      = o.getOrderedAt()  != null ? o.getOrderedAt().toString()  : null;
        dto.cancelledAt    = o.getUpdatedAt()  != null ? o.getUpdatedAt().toString()  : null;

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
            ? o.getItems().stream().mapToInt(i -> i.getQuantity() != null ? i.getQuantity() : 0).sum() : 0;

        dto.items = o.getItems() != null
            ? o.getItems().stream().map(it -> {
                CancelOrderDTO.ItemDTO item = new CancelOrderDTO.ItemDTO();
                item.productName = it.getProductName();
                item.optionName  = it.getOptionName();
                item.quantity    = it.getQuantity() != null ? it.getQuantity() : 0;
                return item;
              }).collect(Collectors.toList())
            : new ArrayList<>();

        return dto;
    }

    private String getProductName(Order o) {
        if (o.getItems() == null || o.getItems().isEmpty()) return null;
        return o.getItems().stream()
            .map(OrderItem::getProductName).filter(Objects::nonNull)
            .collect(Collectors.joining(", "));
    }

    private Product findProduct(OrderItem item) {
        try {
            String key = item.getProductCode() != null ? item.getProductCode() : item.getProductName();
            if (key == null || key.isBlank()) return null;
            List<Product> found = productRepository.searchProducts(key);
            return found.isEmpty() ? null : found.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean contains(String val, String kw) {
        return val != null && val.toLowerCase().contains(kw);
    }
}
