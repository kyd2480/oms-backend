package com.oms.collector.controller;

import com.oms.collector.entity.Order;
import com.oms.collector.repository.OrderRepository;
import com.oms.collector.service.market.MarketShipmentSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/market-shipment")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MarketShipmentController {
    private static final String INVOICE_PREFIX = "INVOICE:";

    private record InvoiceInfo(String carrierCode, String carrierName, String trackingNo) {}

    private final OrderRepository orderRepository;
    private final MarketShipmentSyncService marketShipmentSyncService;

    public record MarketShipmentDto(
        String orderNo,
        String channelCode,
        String channelName,
        String channelOrderNo,
        String recipientName,
        String carrierName,
        String trackingNo,
        String marketSyncStatus,
        String marketSyncMessage,
        String shippedAt,
        String marketSyncAttemptedAt,
        String marketSyncedAt
    ) {
        public static MarketShipmentDto from(Order order) {
            InvoiceInfo invoice = extractInvoiceInfo(order.getDeliveryMemo());
            return new MarketShipmentDto(
                order.getOrderNo(),
                order.getChannel() != null ? order.getChannel().getChannelCode() : "",
                order.getChannel() != null ? order.getChannel().getChannelName() : "",
                order.getChannelOrderNo(),
                order.getRecipientName(),
                invoice != null ? invoice.carrierName() : "",
                invoice != null ? invoice.trackingNo() : "",
                order.getMarketSyncStatus() != null ? order.getMarketSyncStatus().name() : "",
                order.getMarketSyncMessage(),
                formatDateTime(order.getUpdatedAt()),
                formatDateTime(order.getMarketSyncAttemptedAt()),
                formatDateTime(order.getMarketSyncedAt())
            );
        }
    }

    @GetMapping
    public ResponseEntity<List<MarketShipmentDto>> list(
            @RequestParam(defaultValue = "PENDING,FAILED,SUCCESS") String statuses
    ) {
        List<String> wanted = List.of(statuses.split(",")).stream()
            .map(String::trim)
            .filter(it -> !it.isBlank())
            .toList();

        List<Order> shipped = orderRepository.findByOrderStatusWithItems(Order.OrderStatus.SHIPPED);
        shipped.forEach(order -> {
            if (order.getChannel() != null) order.getChannel().getChannelName();
        });

        List<MarketShipmentDto> result = shipped.stream()
            .filter(order -> order.getMarketSyncStatus() != null)
            .filter(order -> wanted.contains(order.getMarketSyncStatus().name()))
            .map(MarketShipmentDto::from)
            .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @PostMapping("/sync/{orderNo}")
    public ResponseEntity<Map<String, Object>> sync(@PathVariable String orderNo) {
        Order order = orderRepository.findWithItemsByOrderNo(orderNo)
            .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다: " + orderNo));

        InvoiceInfo invoice = extractInvoiceInfo(order.getDeliveryMemo());
        if (invoice == null || invoice.trackingNo() == null || invoice.trackingNo().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "송장정보가 없어 판매처 발송완료 전송이 불가합니다"
            ));
        }

        MarketShipmentSyncService.MarketShipmentSyncResult result = marketShipmentSyncService.syncShipment(
            order,
            invoice.carrierCode(),
            invoice.carrierName(),
            invoice.trackingNo()
        );

        return ResponseEntity.ok(Map.of(
            "success", result.success(),
            "message", result.message()
        ));
    }

    @PostMapping("/sync-batch")
    public ResponseEntity<Map<String, Object>> syncBatch(@RequestBody Map<String, List<String>> body) {
        List<String> orderNos = body.getOrDefault("orderNos", List.of());
        int success = 0;
        int failed = 0;

        for (String orderNo : orderNos) {
            try {
                Order order = orderRepository.findWithItemsByOrderNo(orderNo).orElse(null);
                if (order == null) {
                    failed++;
                    continue;
                }
                InvoiceInfo invoice = extractInvoiceInfo(order.getDeliveryMemo());
                if (invoice == null || invoice.trackingNo() == null || invoice.trackingNo().isBlank()) {
                    failed++;
                    continue;
                }
                MarketShipmentSyncService.MarketShipmentSyncResult result = marketShipmentSyncService.syncShipment(
                    order,
                    invoice.carrierCode(),
                    invoice.carrierName(),
                    invoice.trackingNo()
                );
                if (result.success()) success++;
                else failed++;
            } catch (Exception e) {
                failed++;
            }
        }

        return ResponseEntity.ok(Map.of(
            "success", failed == 0,
            "processed", orderNos.size(),
            "synced", success,
            "failed", failed
        ));
    }

    private static String formatDateTime(LocalDateTime value) {
        return value != null ? value.toString() : "";
    }

    private static InvoiceInfo extractInvoiceInfo(String memo) {
        if (memo == null || memo.isBlank()) {
            return null;
        }
        int invoiceIndex = memo.indexOf(INVOICE_PREFIX);
        if (invoiceIndex < 0) {
            return null;
        }

        String carrierCode = null;
        String carrierName = null;
        String trackingNo = null;
        String invoiceSegment = memo.substring(invoiceIndex + INVOICE_PREFIX.length());
        for (String part : invoiceSegment.split("\\|")) {
            String[] kv = part.split(":", 2);
            if (kv.length != 2) {
                continue;
            }
            if (Objects.equals("CARRIER", kv[0])) carrierCode = kv[1];
            if (Objects.equals("CARRIER_NAME", kv[0])) carrierName = kv[1];
            if (Objects.equals("TRACKING", kv[0])) trackingNo = kv[1];
        }
        return new InvoiceInfo(carrierCode, carrierName, trackingNo);
    }
}
