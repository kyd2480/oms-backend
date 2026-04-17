package com.oms.collector.service;

import com.oms.collector.dto.ClaimItemRequest;
import com.oms.collector.dto.ClaimRequest;
import com.oms.collector.entity.Order;
import com.oms.collector.entity.OrderItem;
import com.oms.collector.entity.Product;
import com.oms.collector.entity.ProductReturn;
import com.oms.collector.repository.OrderRepository;
import com.oms.collector.repository.ProductRepository;
import com.oms.collector.repository.ProductReturnRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimProcessingService {
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ProductReturnRepository productReturnRepository;
    private final InventoryService inventoryService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public record ClaimResult(
        boolean success,
        String message,
        String orderNo,
        String claimType,
        String returnId,
        boolean shippingHold
    ) {}

    @Transactional
    public ClaimResult applyClaim(ClaimRequest request) {
        Order order = findOrder(request);
        String claimType = normalizeClaimType(request.claimType);
        return switch (claimType) {
            case "CANCEL" -> applyCancel(order, request);
            case "PARTIAL_CANCEL" -> applyPartialCancel(order, request);
            case "RETURN" -> applyReturn(order, request, ProductReturn.ReturnType.REFUND);
            case "EXCHANGE" -> applyReturn(order, request, ProductReturn.ReturnType.EXCHANGE);
            default -> new ClaimResult(false, "지원하지 않는 클레임 유형: " + request.claimType, order.getOrderNo(), claimType, null, false);
        };
    }

    private Order findOrder(ClaimRequest request) {
        if (request.orderNo != null && !request.orderNo.isBlank()) {
            return orderRepository.findWithItemsByOrderNo(request.orderNo)
                .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다: " + request.orderNo));
        }
        if (request.channelCode != null && !request.channelCode.isBlank()
            && request.channelOrderNo != null && !request.channelOrderNo.isBlank()) {
            return orderRepository.findFirstByChannelChannelCodeAndChannelOrderNo(request.channelCode, request.channelOrderNo)
                .orElseThrow(() -> new RuntimeException("판매처 주문을 찾을 수 없습니다: " + request.channelCode + " / " + request.channelOrderNo));
        }
        throw new RuntimeException("orderNo 또는 channelCode + channelOrderNo가 필요합니다");
    }

    private ClaimResult applyCancel(Order order, ClaimRequest request) {
        boolean hasInvoice = hasInvoice(order);
        String reason = buildReason(request);

        if (order.getOrderStatus() == Order.OrderStatus.SHIPPED) {
            return new ClaimResult(false, "이미 출고된 주문은 취소 API로 처리할 수 없습니다", order.getOrderNo(), "CANCEL", null, false);
        }

        if (hasInvoice) {
            order.setShippingHold(true);
            order.setHoldReason("전체취소 / " + reason);
        }

        if (order.getOrderStatus() == Order.OrderStatus.CONFIRMED) {
            releaseReservedStock(order, order.getItems(), false);
        }

        for (OrderItem item : order.getItems()) {
            int activeQuantity = item.getActiveQuantity();
            if (activeQuantity > 0) {
                item.applyCancelledQuantity(activeQuantity, reason);
            }
        }

        order.setOrderStatus(Order.OrderStatus.CANCELLED);
        orderRepository.save(order);
        return new ClaimResult(true, hasInvoice ? "송장출력 이후 전체취소로 주문 취소 및 출고차단 처리" : "전체취소 처리 완료", order.getOrderNo(), "CANCEL", null, hasInvoice);
    }

    private ClaimResult applyPartialCancel(Order order, ClaimRequest request) {
        if (request.items == null || request.items.isEmpty()) {
            return new ClaimResult(false, "부분취소는 취소 대상 상품이 필요합니다", order.getOrderNo(), "PARTIAL_CANCEL", null, false);
        }
        if (order.getOrderStatus() == Order.OrderStatus.SHIPPED) {
            return new ClaimResult(false, "이미 출고된 주문은 부분취소 API로 처리할 수 없습니다", order.getOrderNo(), "PARTIAL_CANCEL", null, false);
        }

        boolean hasInvoice = hasInvoice(order);
        if (hasInvoice) {
            order.setShippingHold(true);
            order.setHoldReason("부분취소 / " + buildReason(request));
        }

        int appliedItems = 0;
        for (ClaimItemRequest claimItem : request.items) {
            OrderItem target = findMatchingItem(order, claimItem);
            if (target == null) {
                continue;
            }
            int cancelQty = Math.min(target.getActiveQuantity(), claimItem.quantity != null ? claimItem.quantity : 0);
            if (cancelQty <= 0) {
                continue;
            }
            if (order.getOrderStatus() == Order.OrderStatus.CONFIRMED) {
                releaseReservedStock(order, List.of(target), true, cancelQty);
            }
            target.applyCancelledQuantity(cancelQty, buildReason(request));
            appliedItems++;
        }

        if (appliedItems == 0) {
            return new ClaimResult(false, "매칭되는 부분취소 상품이 없습니다", order.getOrderNo(), "PARTIAL_CANCEL", null, hasInvoice);
        }

        if (order.getItems().stream().allMatch(item -> item.getActiveQuantity() <= 0)) {
            order.setOrderStatus(Order.OrderStatus.CANCELLED);
        }

        orderRepository.save(order);
        return new ClaimResult(true, hasInvoice ? "부분취소 반영 및 출고차단 처리" : "부분취소 반영 완료", order.getOrderNo(), "PARTIAL_CANCEL", null, hasInvoice);
    }

    private ClaimResult applyReturn(Order order, ClaimRequest request, ProductReturn.ReturnType returnType) {
        ProductReturn created = ProductReturn.builder()
            .orderNo(order.getOrderNo())
            .channelName(order.getChannel() != null ? order.getChannel().getChannelName() : defaultString(request.channelName))
            .recipientName(defaultString(order.getRecipientName(), request.recipientName))
            .recipientPhone(defaultString(order.getRecipientPhone(), request.recipientPhone))
            .productName(buildProductName(order, request.items))
            .quantity(sumClaimQuantity(request.items, order))
            .returnType(returnType)
            .returnReason(request.claimReason)
            .returnTrackingNo(request.returnTrackingNo)
            .carrierName(request.carrierName)
            .status(ProductReturn.ReturnStatus.REQUESTED)
            .source(defaultString(request.source, "API"))
            .receiveMemo(request.claimMemo)
            .itemsJson(toItemsJson(order, request.items))
            .build();
        productReturnRepository.save(created);

        String message = returnType == ProductReturn.ReturnType.EXCHANGE
            ? "교환 절차 생성 완료"
            : "반품 절차 생성 완료";
        return new ClaimResult(true, message, order.getOrderNo(), returnType.name(), created.getReturnId().toString(), false);
    }

    private void releaseReservedStock(Order order, List<OrderItem> items, boolean partial) {
        for (OrderItem item : items) {
            releaseReservedStock(order, List.of(item), partial, item.getActiveQuantity());
        }
    }

    private void releaseReservedStock(Order order, List<OrderItem> items, boolean partial, int fixedQty) {
        for (OrderItem item : items) {
            Product product = findProduct(item);
            if (product == null) {
                continue;
            }
            int qty = Math.max(0, fixedQty);
            if (qty <= 0) {
                continue;
            }
            try {
                inventoryService.releaseReservedStock(product.getProductId(), qty);
                log.info("예약 재고 해제: orderNo={} product={} qty={} partial={}", order.getOrderNo(), product.getProductName(), qty, partial);
            } catch (Exception e) {
                log.warn("예약 재고 해제 실패: orderNo={} product={} msg={}", order.getOrderNo(), product.getProductName(), e.getMessage());
            }
        }
    }

    private Product findProduct(OrderItem item) {
        String key = item.getProductCode() != null && !item.getProductCode().isBlank()
            ? item.getProductCode()
            : item.getProductName();
        if (key == null || key.isBlank()) {
            return null;
        }
        List<Product> found = productRepository.searchProducts(key);
        return found.stream()
            .filter(p -> key.equalsIgnoreCase(p.getSku()) || key.equalsIgnoreCase(p.getBarcode()))
            .findFirst()
            .orElse(found.isEmpty() ? null : found.get(0));
    }

    private OrderItem findMatchingItem(Order order, ClaimItemRequest request) {
        return order.getItems().stream()
            .filter(item -> item.getActiveQuantity() > 0)
            .filter(item -> matches(item, request))
            .findFirst()
            .orElse(null);
    }

    private boolean matches(OrderItem item, ClaimItemRequest request) {
        boolean codeMatch = request.productCode != null && !request.productCode.isBlank()
            && (request.productCode.equalsIgnoreCase(defaultString(item.getProductCode()))
                || request.productCode.equalsIgnoreCase(defaultString(item.getChannelProductCode())));
        boolean channelCodeMatch = request.channelProductCode != null && !request.channelProductCode.isBlank()
            && request.channelProductCode.equalsIgnoreCase(defaultString(item.getChannelProductCode()));
        boolean nameMatch = request.productName != null && !request.productName.isBlank()
            && request.productName.equalsIgnoreCase(defaultString(item.getProductName()));
        boolean optionMatch = request.optionName == null || request.optionName.isBlank()
            || request.optionName.equalsIgnoreCase(defaultString(item.getOptionName()));
        return (codeMatch || channelCodeMatch || nameMatch) && optionMatch;
    }

    private String toItemsJson(Order order, List<ClaimItemRequest> items) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (items == null || items.isEmpty()) {
            for (OrderItem item : order.getItems()) {
                if (item.getActiveQuantity() <= 0) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("productName", item.getProductName());
                row.put("optionName", item.getOptionName());
                row.put("productCode", item.getProductCode());
                row.put("quantity", item.getActiveQuantity());
                rows.add(row);
            }
        } else {
            for (ClaimItemRequest item : items) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("productName", item.productName);
                row.put("optionName", item.optionName);
                row.put("productCode", item.productCode != null ? item.productCode : item.channelProductCode);
                row.put("quantity", item.quantity != null ? item.quantity : 1);
                rows.add(row);
            }
        }
        try {
            return objectMapper.writeValueAsString(rows);
        } catch (Exception e) {
            throw new RuntimeException("클레임 상품 직렬화 실패", e);
        }
    }

    private int sumClaimQuantity(List<ClaimItemRequest> items, Order order) {
        if (items == null || items.isEmpty()) {
            return order.getItems().stream().mapToInt(OrderItem::getActiveQuantity).sum();
        }
        return items.stream().mapToInt(it -> it.quantity != null ? it.quantity : 0).sum();
    }

    private String buildProductName(Order order, List<ClaimItemRequest> items) {
        if (items != null && !items.isEmpty()) {
            return items.stream()
                .map(it -> it.productName)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.joining(", "));
        }
        return order.getItems().stream()
            .filter(item -> item.getActiveQuantity() > 0)
            .map(OrderItem::getProductName)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.joining(", "));
    }

    private boolean hasInvoice(Order order) {
        return order.getDeliveryMemo() != null && order.getDeliveryMemo().contains("TRACKING:");
    }

    private String buildReason(ClaimRequest request) {
        String type = normalizeClaimType(request.claimType);
        String reason = defaultString(request.claimReason);
        String memo = defaultString(request.claimMemo);
        return (type + (reason.isBlank() ? "" : " / " + reason) + (memo.isBlank() ? "" : " / " + memo)).trim();
    }

    private String normalizeClaimType(String type) {
        if (type == null) {
            return "";
        }
        return type.trim().toUpperCase()
            .replace("PARTIAL-CANCEL", "PARTIAL_CANCEL")
            .replace("PARTIAL CANCEL", "PARTIAL_CANCEL");
    }

    private String defaultString(String value) {
        return value != null ? value : "";
    }

    private String defaultString(String first, String second) {
        return first != null && !first.isBlank() ? first : defaultString(second);
    }
}
