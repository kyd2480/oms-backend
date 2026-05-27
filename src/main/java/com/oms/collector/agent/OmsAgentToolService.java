package com.oms.collector.agent;

import com.oms.collector.entity.Order;
import com.oms.collector.entity.OrderItem;
import com.oms.collector.entity.PrintType;
import com.oms.collector.entity.Product;
import com.oms.collector.entity.ProductReturn;
import com.oms.collector.repository.OrderRepository;
import com.oms.collector.repository.PrintTypeRepository;
import com.oms.collector.repository.ProductRepository;
import com.oms.collector.repository.ProductReturnRepository;
import com.oms.collector.service.ProductSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
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
    private static final String INVOICE_PREFIX = "INVOICE:";

    private final OrderRepository orderRepository;
    private final PrintTypeRepository printTypeRepository;
    private final ProductRepository productRepository;
    private final ProductReturnRepository productReturnRepository;
    private final ProductSearchService productSearchService;

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
            .map(o -> {
                InvoiceInfo invoice = extractInvoiceInfo(o.getDeliveryMemo());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("orderNo", o.getOrderNo());
                row.put("status", o.getOrderStatus().name());
                row.put("channelName", o.getChannel() != null ? o.getChannel().getChannelName() : "");
                row.put("recipientName", nullable(o.getRecipientName()));
                row.put("customerName", nullable(o.getCustomerName()));
                row.put("orderedAt", o.getOrderedAt() != null ? o.getOrderedAt().toString() : "");
                row.put("productSummary", summarizeItems(o));
                row.put("invoiceEntered", invoice != null);
                row.put("carrierCode", invoice != null ? nullable(invoice.carrierCode()) : "");
                row.put("carrierName", invoice != null ? nullable(invoice.carrierName()) : "");
                row.put("trackingNo", invoice != null ? nullable(invoice.trackingNo()) : "");
                return row;
            })
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

    public Map<String, Object> getInvoicePendingOverview() {
        long pending = orderRepository.countInvoicePendingOrders();
        long assigned = orderRepository.countInvoiceAssignedOrders();
        return Map.of(
            "invoicePendingOrders", pending,
            "invoiceAssignedOrders", assigned,
            "totalConfirmedOrders", pending + assigned
        );
    }

    public Map<String, Object> getOperationalStatusOverview() {
        long invoicePending = orderRepository.countInvoicePendingOrders();
        long inspectionWaiting = orderRepository.countInvoiceAssignedOrders();
        long confirmedOrders = orderRepository.countByOrderStatus(Order.OrderStatus.CONFIRMED);
        long shippedOrders = orderRepository.countByOrderStatus(Order.OrderStatus.SHIPPED);
        long deliveredOrders = orderRepository.countByOrderStatus(Order.OrderStatus.DELIVERED);

        return Map.of(
            "pendingOrders", orderRepository.countByOrderStatus(Order.OrderStatus.PENDING),
            "unmatchedItems", safeLong(orderRepository.countPendingUnmatched()),
            "allocatedItems", orderRepository.countConfirmedAllocatedItems(),
            "allocatedOrders", confirmedOrders,
            "invoicePendingOrders", invoicePending,
            "inspectionWaitingOrders", inspectionWaiting,
            "shippedOrders", shippedOrders,
            "deliveredOrders", deliveredOrders,
            "cancelledOrders", orderRepository.countByOrderStatus(Order.OrderStatus.CANCELLED),
            "shippingHoldOrders", orderRepository.countByShippingHoldTrue()
        );
    }

    public Map<String, Object> searchProducts(String keyword, Integer limit) {
        int safeLimit = Math.min(Math.max(limit == null ? 10 : limit, 1), 20);
        List<Product> products = productSearchService.search(nullable(keyword), safeLimit).stream()
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

    public Map<String, Object> getShipmentStats(LocalDate startDate, LocalDate endDate, Integer limit) {
        int safeLimit = Math.min(Math.max(limit == null ? 10 : limit, 1), 50);
        LocalDate safeStartDate = startDate != null ? startDate : LocalDate.now(OMS_ZONE);
        LocalDate safeEndDate = endDate != null ? endDate : safeStartDate;
        if (safeEndDate.isBefore(safeStartDate)) {
            LocalDate tmp = safeStartDate;
            safeStartDate = safeEndDate;
            safeEndDate = tmp;
        }

        LocalDateTime start = safeStartDate.atStartOfDay();
        LocalDateTime end = safeEndDate.atTime(23, 59, 59);
        List<Order> orders = orderRepository.findByOrderStatusAndUpdatedAtRange(Order.OrderStatus.SHIPPED, start, end);

        int totalQuantity = orders.stream()
            .flatMap(order -> order.getItems().stream())
            .mapToInt(item -> item.getQuantity() != null ? item.getQuantity() : 0)
            .sum();

        List<Map<String, Object>> samples = orders.stream()
            .limit(safeLimit)
            .map(o -> Map.<String, Object>of(
                "orderNo", nullable(o.getOrderNo()),
                "status", o.getOrderStatus() != null ? o.getOrderStatus().name() : "",
                "channelName", o.getChannel() != null ? nullable(o.getChannel().getChannelName()) : "",
                "recipientName", nullable(o.getRecipientName()),
                "shippedAt", o.getUpdatedAt() != null ? o.getUpdatedAt().toString() : "",
                "productSummary", summarizeItems(o)
            ))
            .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("zone", OMS_ZONE.getId());
        result.put("startDate", safeStartDate.toString());
        result.put("endDate", safeEndDate.toString());
        result.put("shippedCount", orders.size());
        result.put("totalQuantity", totalQuantity);
        result.put("orders", samples);
        return result;
    }

    public Map<String, Object> getClaimOverview(String claimType, LocalDate startDate, LocalDate endDate, Integer limit) {
        int safeLimit = Math.min(Math.max(limit == null ? 10 : limit, 1), 50);
        LocalDate safeStartDate = startDate != null ? startDate : LocalDate.now(OMS_ZONE);
        LocalDate safeEndDate = endDate != null ? endDate : safeStartDate;
        if (safeEndDate.isBefore(safeStartDate)) {
            LocalDate tmp = safeStartDate;
            safeStartDate = safeEndDate;
            safeEndDate = tmp;
        }

        LocalDateTime start = safeStartDate.atStartOfDay();
        LocalDateTime end = safeEndDate.atTime(23, 59, 59);
        String normalizedType = nullable(claimType).trim().toUpperCase(Locale.ROOT);

        List<ProductReturn> claims = productReturnRepository.findByDateRange(start, end).stream()
            .filter(claim -> switch (normalizedType) {
                case "EXCHANGE" -> claim.getReturnType() == ProductReturn.ReturnType.EXCHANGE;
                case "RETURN" -> claim.getReturnType() == ProductReturn.ReturnType.REFUND || claim.getReturnType() == ProductReturn.ReturnType.CANCEL;
                default -> true;
            })
            .toList();

        long requested = claims.stream().filter(claim -> claim.getStatus() == ProductReturn.ReturnStatus.REQUESTED).count();
        long inspecting = claims.stream().filter(claim -> claim.getStatus() == ProductReturn.ReturnStatus.INSPECTING).count();
        long completed = claims.stream().filter(claim -> claim.getStatus() == ProductReturn.ReturnStatus.COMPLETED).count();
        long cancelled = claims.stream().filter(claim -> claim.getStatus() == ProductReturn.ReturnStatus.CANCELLED).count();

        List<Map<String, Object>> items = claims.stream()
            .limit(safeLimit)
            .map(claim -> Map.<String, Object>of(
                "returnId", claim.getReturnId() != null ? claim.getReturnId().toString() : "",
                "orderNo", nullable(claim.getOrderNo()),
                "returnType", claim.getReturnType() != null ? claim.getReturnType().name() : "",
                "status", claim.getStatus() != null ? claim.getStatus().name() : "",
                "recipientName", nullable(claim.getRecipientName()),
                "productName", nullable(claim.getProductName()),
                "createdAt", claim.getCreatedAt() != null ? claim.getCreatedAt().toString() : ""
            ))
            .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("zone", OMS_ZONE.getId());
        result.put("claimType", normalizedType.isBlank() ? "ALL" : normalizedType);
        result.put("startDate", safeStartDate.toString());
        result.put("endDate", safeEndDate.toString());
        result.put("totalClaims", claims.size());
        result.put("requestedClaims", requested);
        result.put("inspectingClaims", inspecting);
        result.put("completedClaims", completed);
        result.put("cancelledClaims", cancelled);
        result.put("claims", items);
        return result;
    }

    public Map<String, Object> getTopProductsByChannel(LocalDate startDate, LocalDate endDate, String channelKeyword, Integer limit) {
        int safeLimit = Math.min(Math.max(limit == null ? 3 : limit, 1), 10);
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23, 59, 59);

        List<Order> orders = orderRepository.findByDateRange(start, end).stream()
            .filter(order -> {
                String channelName = order.getChannel() != null ? nullable(order.getChannel().getChannelName()) : "";
                return channelKeyword == null || channelKeyword.isBlank()
                    || channelName.toLowerCase(Locale.ROOT).contains(channelKeyword.toLowerCase(Locale.ROOT));
            })
            .toList();

        Map<String, ProductSummary> summaryMap = new LinkedHashMap<>();
        for (Order order : orders) {
            for (OrderItem item : order.getItems()) {
                String productName = nullable(item.getProductName()).isBlank() ? "상품명 없음" : item.getProductName().trim();
                ProductSummary summary = summaryMap.computeIfAbsent(productName, key -> new ProductSummary());
                summary.quantity += item.getQuantity() != null ? item.getQuantity() : 0;
                summary.orderNos.add(nullable(order.getOrderNo()));
            }
        }

        List<Map<String, Object>> products = summaryMap.entrySet().stream()
            .sorted((a, b) -> {
                int quantityCompare = Integer.compare(b.getValue().quantity, a.getValue().quantity);
                if (quantityCompare != 0) {
                    return quantityCompare;
                }
                return Integer.compare(b.getValue().orderNos.size(), a.getValue().orderNos.size());
            })
            .limit(safeLimit)
            .map(entry -> Map.<String, Object>of(
                "productName", entry.getKey(),
                "quantity", entry.getValue().quantity,
                "orderCount", entry.getValue().orderNos.size()
            ))
            .toList();

        return Map.of(
            "startDate", startDate.toString(),
            "endDate", endDate.toString(),
            "channelKeyword", nullable(channelKeyword),
            "orderCount", orders.size(),
            "products", products
        );
    }

    public Map<String, Object> searchOrdersByPrintType(String printTypeKeyword, Integer limit) {
        int safeLimit = Math.min(Math.max(limit == null ? 10 : limit, 1), 20);
        PrintType printType = resolvePrintType(printTypeKeyword);
        if (printType == null) {
            return Map.of(
                "keyword", nullable(printTypeKeyword),
                "matched", false,
                "count", 0,
                "orders", List.of()
            );
        }

        List<Map<String, Object>> items = orderRepository.findByPrintTypeCodeAndOrderStatusNot(
                printType.getCode(),
                Order.OrderStatus.CANCELLED
            ).stream()
            .sorted((a, b) -> {
                LocalDateTime left = a.getOrderedAt();
                LocalDateTime right = b.getOrderedAt();
                if (left == null && right == null) return 0;
                if (left == null) return 1;
                if (right == null) return -1;
                return right.compareTo(left);
            })
            .limit(safeLimit)
            .map(o -> Map.<String, Object>of(
                "orderNo", nullable(o.getOrderNo()),
                "status", o.getOrderStatus() != null ? o.getOrderStatus().name() : "",
                "channelName", o.getChannel() != null ? nullable(o.getChannel().getChannelName()) : "",
                "recipientName", nullable(o.getRecipientName()),
                "orderedAt", o.getOrderedAt() != null ? o.getOrderedAt().toString() : "",
                "productSummary", summarizeItems(o)
            ))
            .toList();

        return Map.of(
            "keyword", nullable(printTypeKeyword),
            "matched", true,
            "printTypeCode", nullable(printType.getCode()),
            "printTypeName", nullable(printType.getName()),
            "count", items.size(),
            "orders", items
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
            case "get_shipment_stats" -> getShipmentStats(
                LocalDate.parse(stringArg(args, "startDate", LocalDate.now(OMS_ZONE).toString())),
                LocalDate.parse(stringArg(args, "endDate", stringArg(args, "startDate", LocalDate.now(OMS_ZONE).toString()))),
                intArg(args, "limit", 10)
            );
            case "get_claim_overview" -> getClaimOverview(
                stringArg(args, "claimType", "ALL"),
                LocalDate.parse(stringArg(args, "startDate", LocalDate.now(OMS_ZONE).toString())),
                LocalDate.parse(stringArg(args, "endDate", stringArg(args, "startDate", LocalDate.now(OMS_ZONE).toString()))),
                intArg(args, "limit", 10)
            );
            case "get_inventory_overview" -> getInventoryOverview();
            case "get_invoice_pending_overview" -> getInvoicePendingOverview();
            case "get_operational_status_overview" -> getOperationalStatusOverview();
            case "search_products" -> searchProducts(
                stringArg(args, "keyword", ""),
                intArg(args, "limit", 10)
            );
            case "get_top_products_by_channel" -> getTopProductsByChannel(
                LocalDate.parse(stringArg(args, "startDate", LocalDate.now(OMS_ZONE).minusDays(29).toString())),
                LocalDate.parse(stringArg(args, "endDate", LocalDate.now(OMS_ZONE).toString())),
                stringArg(args, "channelKeyword", ""),
                intArg(args, "limit", 3)
            );
            case "search_orders_by_print_type" -> searchOrdersByPrintType(
                stringArg(args, "printTypeKeyword", ""),
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

    private long safeLong(Long value) {
        return value != null ? value : 0L;
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

    private PrintType resolvePrintType(String keyword) {
        String normalized = normalize(keyword);
        if (normalized.isBlank()) {
            return null;
        }
        return printTypeRepository.findAllByOrderBySortOrderAscNameAsc().stream()
            .filter(type -> containsPrintTypeKeyword(type, normalized))
            .findFirst()
            .orElse(null);
    }

    private boolean containsPrintTypeKeyword(PrintType type, String normalizedKeyword) {
        String code = normalize(type.getCode());
        String name = normalize(type.getName());
        return code.contains(normalizedKeyword)
            || name.contains(normalizedKeyword)
            || normalizedKeyword.contains(code)
            || normalizedKeyword.contains(name);
    }

    private String nullable(String value) {
        return value != null ? value : "";
    }

    private InvoiceInfo extractInvoiceInfo(String memo) {
        if (memo == null || memo.isBlank()) {
            return null;
        }
        int invoiceIndex = memo.indexOf(INVOICE_PREFIX);
        if (invoiceIndex < 0) {
            return null;
        }

        String carrierCode = "";
        String carrierName = "";
        String trackingNo = "";
        String invoiceSegment = memo.substring(invoiceIndex + INVOICE_PREFIX.length());
        for (String part : invoiceSegment.split("\\|")) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            switch (kv[0]) {
                case "CARRIER" -> carrierCode = kv[1];
                case "CARRIER_NAME" -> carrierName = kv[1];
                case "TRACKING" -> trackingNo = kv[1];
                default -> {
                }
            }
        }
        return new InvoiceInfo(carrierCode, carrierName, trackingNo);
    }

    private record InvoiceInfo(String carrierCode, String carrierName, String trackingNo) {}

    private static class ProductSummary {
        private int quantity;
        private final LinkedHashSet<String> orderNos = new LinkedHashSet<>();
    }
}
