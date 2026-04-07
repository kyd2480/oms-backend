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

import java.time.ZoneId;
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

    private static final ZoneId OMS_ZONE = ZoneId.of("Asia/Seoul");

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    public Map<String, Object> getOrderOverview(String period) {
        LocalDate today = LocalDate.now(OMS_ZONE);
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

        Map<String, Long> dailyCounts = orders.stream()
            .filter(o -> o.getOrderedAt() != null)
            .collect(Collectors.groupingBy(
                o -> o.getOrderedAt().atZone(OMS_ZONE).toLocalDate().toString(),
                LinkedHashMap::new,
                Collectors.counting()
            ));

        List<Map<String, Object>> recentDailyCounts = dailyCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByKey().reversed())
            .limit(7)
            .map(e -> Map.<String, Object>of("date", e.getKey(), "count", e.getValue()))
            .toList();

        Order latestOrder = orderRepository.findFirstByOrderByOrderedAtDesc().orElse(null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", normalize(period));
        result.put("zone", OMS_ZONE.getId());
        result.put("startDate", startDate.toString());
        result.put("endDate", today.toString());
        result.put("totalOrders", orders.size());
        result.put("pendingOrders", pending);
        result.put("confirmedOrders", confirmed);
        result.put("shippedOrders", shipped);
        result.put("cancelledOrders", cancelled);
        result.put("topChannels", topChannels);
        result.put("recentDailyCounts", recentDailyCounts);
        result.put("latestOrderNo", latestOrder != null ? nullable(latestOrder.getOrderNo()) : "");
        result.put("latestOrderedAt", latestOrder != null && latestOrder.getOrderedAt() != null
            ? latestOrder.getOrderedAt().atZone(OMS_ZONE).toLocalDateTime().toString()
            : "");
        return result;
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

    public Map<String, Object> executeTool(String name, Map<String, Object> args) {
        return switch (name) {
            case "get_order_overview" -> getOrderOverview(stringArg(args, "period", "7d"));
            case "search_orders" -> searchOrders(
                stringArg(args, "keyword", ""),
                stringArg(args, "status", "ALL"),
                intArg(args, "limit", 10)
            );
            case "get_inventory_overview" -> getInventoryOverview();
            case "search_products" -> searchProducts(
                stringArg(args, "keyword", ""),
                intArg(args, "limit", 10)
            );
            default -> {
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("error", "Unknown tool: " + name);
                yield error;
            }
        };
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

    private int intArg(Map<String, Object> args, String key, int defaultValue) {
        Object value = args != null ? args.get(key) : null;
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private String stringArg(Map<String, Object> args, String key, String defaultValue) {
        Object value = args != null ? args.get(key) : null;
        return value != null ? String.valueOf(value) : defaultValue;
    }

    private String nullable(String value) {
        return value != null ? value : "";
    }
}
