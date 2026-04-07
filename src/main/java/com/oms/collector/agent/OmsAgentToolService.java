package com.oms.collector.agent;

import com.oms.collector.entity.Order;
import com.oms.collector.entity.Product;
import com.oms.collector.repository.OrderRepository;
import com.oms.collector.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OmsAgentToolService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    public Map<String, Object> getOrderOverview(String period) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = switch (normalize(period)) {
            case "today" -> today;
            case "30d" -> today.minusDays(29);
            default -> today.minusDays(6);
        };
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = today.atTime(23, 59, 59);

        List<Order> orders = orderRepository.findByDateRange(start, end);
        long pending = orders.stream().filter(o -> o.getOrderStatus() == Order.OrderStatus.PENDING).count();
        long confirmed = orders.stream().filter(o -> o.getOrderStatus() == Order.OrderStatus.CONFIRMED).count();
        long shipped = orders.stream().filter(o -> o.getOrderStatus() == Order.OrderStatus.SHIPPED).count();
        long cancelled = orders.stream().filter(o -> o.getOrderStatus() == Order.OrderStatus.CANCELLED).count();

        Map<String, Long> byChannel = orders.stream()
            .collect(Collectors.groupingBy(
                o -> o.getChannel() != null ? o.getChannel().getChannelName() : "미분류",
                LinkedHashMap::new,
                Collectors.counting()
            ));

        List<Map<String, Object>> topChannels = byChannel.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
            .limit(5)
            .map(e -> Map.<String, Object>of("channelName", e.getKey(), "count", e.getValue()))
            .toList();

        return Map.of(
            "period", normalize(period),
            "startDate", startDate.toString(),
            "endDate", today.toString(),
            "totalOrders", orders.size(),
            "pendingOrders", pending,
            "confirmedOrders", confirmed,
            "shippedOrders", shipped,
            "cancelledOrders", cancelled,
            "topChannels", topChannels
        );
    }

    public Map<String, Object> searchOrders(String keyword, String status, Integer limit) {
        int safeLimit = Math.min(Math.max(limit == null ? 10 : limit, 1), 20);
        Order.OrderStatus orderStatus = parseStatus(status);
        List<Order> orders = orderRepository.searchForAgent(keyword, orderStatus, PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "orderedAt")));

        List<Map<String, Object>> items = orders.stream()
            .map(o -> Map.<String, Object>of(
                "orderNo", o.getOrderNo(),
                "status", o.getOrderStatus().name(),
                "channelName", o.getChannel() != null ? o.getChannel().getChannelName() : "",
                "recipientName", nullable(o.getRecipientName()),
                "customerName", nullable(o.getCustomerName()),
                "orderedAt", o.getOrderedAt() != null ? o.getOrderedAt().toString() : "",
                "productSummary", summarizeItems(o),
                "invoiceEntered", o.getDeliveryMemo() != null && o.getDeliveryMemo().startsWith("INVOICE:")
            ))
            .toList();

        return Map.of(
            "keyword", nullable(keyword),
            "status", orderStatus != null ? orderStatus.name() : "ALL",
            "count", items.size(),
            "orders", items
        );
    }

    public Map<String, Object> getInventoryOverview() {
        List<Product> products = productRepository.findByIsActiveTrueOrderByProductNameAsc();
        List<Product> outOfStock = productRepository.findOutOfStockProducts();

        int totalProducts = products.size();
        int totalStock = products.stream().mapToInt(p -> safeInt(p.getTotalStock())).sum();
        int availableStock = products.stream().mapToInt(p -> safeInt(p.getAvailableStock())).sum();
        int reservedStock = products.stream().mapToInt(p -> safeInt(p.getReservedStock())).sum();

        List<Map<String, Object>> riskProducts = products.stream()
            .sorted((a, b) -> Integer.compare(safeInt(a.getAvailableStock()), safeInt(b.getAvailableStock())))
            .limit(10)
            .map(p -> Map.<String, Object>of(
                "sku", nullable(p.getSku()),
                "productName", nullable(p.getProductName()),
                "availableStock", safeInt(p.getAvailableStock()),
                "reservedStock", safeInt(p.getReservedStock()),
                "totalStock", safeInt(p.getTotalStock())
            ))
            .toList();

        return Map.of(
            "totalProducts", totalProducts,
            "totalStock", totalStock,
            "availableStock", availableStock,
            "reservedStock", reservedStock,
            "outOfStockCount", outOfStock.size(),
            "riskProducts", riskProducts
        );
    }

    public Map<String, Object> searchProducts(String keyword, Integer limit) {
        int safeLimit = Math.min(Math.max(limit == null ? 10 : limit, 1), 20);
        List<Product> products = productRepository.searchProducts(nullable(keyword)).stream()
            .limit(safeLimit)
            .toList();

        List<Map<String, Object>> items = products.stream()
            .map(p -> Map.<String, Object>of(
                "sku", nullable(p.getSku()),
                "productName", nullable(p.getProductName()),
                "barcode", nullable(p.getBarcode()),
                "availableStock", safeInt(p.getAvailableStock()),
                "reservedStock", safeInt(p.getReservedStock()),
                "totalStock", safeInt(p.getTotalStock()),
                "location", nullable(p.getWarehouseLocation())
            ))
            .toList();

        return Map.of(
            "keyword", nullable(keyword),
            "count", items.size(),
            "products", items
        );
    }

    private String summarizeItems(Order order) {
        return order.getItems().stream()
            .limit(3)
            .map(i -> {
                String name = nullable(i.getProductName());
                int qty = i.getQuantity() != null ? i.getQuantity() : 0;
                return name + " x" + qty;
            })
            .collect(Collectors.joining(", "));
    }

    private String normalize(String period) {
        String value = nullable(period).trim().toLowerCase(Locale.ROOT);
        if (value.isBlank()) return "7d";
        return value;
    }

    private Order.OrderStatus parseStatus(String status) {
        String value = nullable(status).trim().toUpperCase(Locale.ROOT);
        if (value.isBlank() || "ALL".equals(value)) {
            return null;
        }
        try {
            return Order.OrderStatus.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    private String nullable(String value) {
        return value != null ? value : "";
    }
}
