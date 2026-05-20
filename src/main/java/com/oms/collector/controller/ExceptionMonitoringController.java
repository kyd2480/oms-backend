package com.oms.collector.controller;

import com.oms.collector.entity.InvoiceApiLog;
import com.oms.collector.entity.Order;
import com.oms.collector.entity.Product;
import com.oms.collector.entity.ProductReturn;
import com.oms.collector.entity.RawOrder;
import com.oms.collector.repository.InvoiceApiLogRepository;
import com.oms.collector.repository.OrderRepository;
import com.oms.collector.repository.ProductRepository;
import com.oms.collector.repository.ProductReturnRepository;
import com.oms.collector.repository.RawOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class ExceptionMonitoringController {

    private final RawOrderRepository rawOrderRepository;
    private final OrderRepository orderRepository;
    private final InvoiceApiLogRepository invoiceApiLogRepository;
    private final ProductRepository productRepository;
    private final ProductReturnRepository productReturnRepository;

    public record MonitorSummary(
        long collectionFailures,
        long marketShipmentFailures,
        long invoiceApiFailures,
        long negativeStockProducts,
        long pendingClaims,
        long heldOrders,
        long inspectionLeaks
    ) {}

    public record MonitorItem(
        String primary,
        String secondary,
        String detail,
        String status,
        String occurredAt
    ) {}

    public record ExceptionMonitoringResponse(
        String generatedAt,
        MonitorSummary summary,
        List<MonitorItem> collectionFailureItems,
        List<MonitorItem> marketShipmentFailureItems,
        List<MonitorItem> invoiceApiFailureItems,
        List<MonitorItem> negativeStockItems,
        List<MonitorItem> pendingClaimItems,
        List<MonitorItem> heldOrderItems,
        List<MonitorItem> inspectionLeakItems
    ) {}

    @GetMapping("/exception-monitoring")
    public ExceptionMonitoringResponse getExceptionMonitoring() {
        var pendingClaimStatuses = List.of(
            ProductReturn.ReturnStatus.REQUESTED,
            ProductReturn.ReturnStatus.INSPECTING
        );

        long collectionFailures = rawOrderRepository.countByProcessedFalseAndErrorMessageIsNotNull();
        long marketShipmentFailures = orderRepository.countByMarketSyncStatus(Order.MarketSyncStatus.FAILED);
        long invoiceApiFailures = invoiceApiLogRepository.countBySuccessFalse();
        long negativeStockProducts = productRepository.countNegativeAvailableStock();
        long pendingClaims = productReturnRepository.countByStatusIn(pendingClaimStatuses);
        long heldOrders = orderRepository.countByShippingHoldTrue();
        long inspectionLeaks = orderRepository.countByOrderStatusAndInspectionCompletedFalse(Order.OrderStatus.SHIPPED);

        return new ExceptionMonitoringResponse(
            LocalDateTime.now().toString(),
            new MonitorSummary(
                collectionFailures,
                marketShipmentFailures,
                invoiceApiFailures,
                negativeStockProducts,
                pendingClaims,
                heldOrders,
                inspectionLeaks
            ),
            rawOrderRepository.findTop10ByProcessedFalseAndErrorMessageIsNotNullOrderByCollectedAtDesc()
                .stream()
                .map(this::toCollectionFailureItem)
                .toList(),
            orderRepository.findTop10ByMarketSyncStatusOrderByMarketSyncAttemptedAtDesc(Order.MarketSyncStatus.FAILED)
                .stream()
                .map(this::toMarketShipmentFailureItem)
                .toList(),
            invoiceApiLogRepository.findTop10BySuccessFalseOrderByCreatedAtDesc()
                .stream()
                .map(this::toInvoiceApiFailureItem)
                .toList(),
            productRepository.findTop10NegativeAvailableStock()
                .stream()
                .map(this::toNegativeStockItem)
                .toList(),
            productReturnRepository.findTop10ByStatusInOrderByUpdatedAtDesc(pendingClaimStatuses)
                .stream()
                .map(this::toPendingClaimItem)
                .toList(),
            orderRepository.findTop10ByShippingHoldTrueOrderByUpdatedAtDesc()
                .stream()
                .map(this::toHeldOrderItem)
                .toList(),
            orderRepository.findByOrderStatusInAndUpdatedAtRange(
                    List.of(Order.OrderStatus.SHIPPED),
                    LocalDateTime.now().minusDays(30),
                    LocalDateTime.now().plusSeconds(1))
                .stream()
                .filter(order -> !Boolean.TRUE.equals(order.getInspectionCompleted()))
                .limit(10)
                .map(this::toInspectionLeakItem)
                .toList()
        );
    }

    private MonitorItem toCollectionFailureItem(RawOrder rawOrder) {
        return new MonitorItem(
            safe(rawOrder.getChannelOrderNo()),
            rawOrder.getChannel() != null ? safe(rawOrder.getChannel().getChannelName()) : "미분류",
            safe(rawOrder.getErrorMessage()),
            "수집 실패",
            rawOrder.getCollectedAt() != null ? rawOrder.getCollectedAt().toString() : ""
        );
    }

    private MonitorItem toMarketShipmentFailureItem(Order order) {
        return new MonitorItem(
            safe(order.getOrderNo()),
            order.getChannel() != null ? safe(order.getChannel().getChannelName()) : "미분류",
            safe(order.getMarketSyncMessage()),
            "전송 실패",
            order.getMarketSyncAttemptedAt() != null
                ? order.getMarketSyncAttemptedAt().toString()
                : (order.getUpdatedAt() != null ? order.getUpdatedAt().toString() : "")
        );
    }

    private MonitorItem toInvoiceApiFailureItem(InvoiceApiLog log) {
        return new MonitorItem(
            safe(log.getOrderNo()),
            safe(log.getTrackingNo()),
            safe(log.getResponseMessage()),
            log.getActionType() == null ? "API 실패" : log.getActionType().name(),
            log.getCreatedAt() != null ? log.getCreatedAt().toString() : ""
        );
    }

    private MonitorItem toNegativeStockItem(Product product) {
        return new MonitorItem(
            safe(product.getSku()),
            safe(product.getProductName()),
            "가용재고 " + String.valueOf(product.getAvailableStock()),
            "음수 재고",
            product.getUpdatedAt() != null ? product.getUpdatedAt().toString() : ""
        );
    }

    private MonitorItem toPendingClaimItem(ProductReturn productReturn) {
        return new MonitorItem(
            safe(productReturn.getOrderNo()),
            safe(productReturn.getProductName()),
            safe(productReturn.getReturnType() != null ? productReturn.getReturnType().name() : ""),
            productReturn.getStatus() != null ? productReturn.getStatus().name() : "대기",
            productReturn.getUpdatedAt() != null
                ? productReturn.getUpdatedAt().toString()
                : (productReturn.getCreatedAt() != null ? productReturn.getCreatedAt().toString() : "")
        );
    }

    private MonitorItem toHeldOrderItem(Order order) {
        return new MonitorItem(
            safe(order.getOrderNo()),
            safe(order.getRecipientName()),
            safe(order.getHoldReason()),
            "보류",
            order.getUpdatedAt() != null ? order.getUpdatedAt().toString() : ""
        );
    }

    private MonitorItem toInspectionLeakItem(Order order) {
        return new MonitorItem(
            safe(order.getOrderNo()),
            safe(order.getRecipientName()),
            extractTrackingNo(order.getDeliveryMemo()),
            "검수누락 의심",
            order.getUpdatedAt() != null ? order.getUpdatedAt().toString() : ""
        );
    }

    private String extractTrackingNo(String memo) {
        if (memo == null || memo.isBlank()) return "";
        int idx = memo.indexOf("TRACKING:");
        if (idx < 0) return "";
        String tail = memo.substring(idx + "TRACKING:".length()).trim();
        int sep = tail.indexOf('|');
        return sep >= 0 ? tail.substring(0, sep).trim() : tail;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
