package com.oms.collector.controller;

import com.oms.collector.entity.Order;
import com.oms.collector.entity.OrderItem;
import com.oms.collector.entity.Product;
import com.oms.collector.repository.OrderRepository;
import com.oms.collector.repository.ProductRepository;
import com.oms.collector.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 재고 매칭 컨트롤러
 * - 할당된 창고의 재고 기준으로 주문별 출고 가능 여부 분류
 * - 완전출고 / 부분출고 / 출고불가 / 상품미매칭
 *
 * GET  /api/stock-matching/match      - 재고 매칭 실행
 * POST /api/stock-matching/reserve    - 매칭 결과 기준 재고 예약
 */
@Slf4j
@RestController
@RequestMapping("/api/stock-matching")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class StockMatchingController {

    private final OrderRepository   orderRepository;
    private final ProductRepository productRepository;
    private final InventoryService  inventoryService;

    // ─── DTO ─────────────────────────────────────────────────────

    public static class MatchItemDTO {
        public String orderNo;
        public String channelName;
        public String recipientName;
        public String productName;
        public String productCode;
        public String sku;
        public int    ordered;
        public int    warehouseStock;
        public int    allocatable;
        public String shipStatus;  // FULL / PARTIAL / IMPOSSIBLE / NOT_MATCHED
        public String orderedAt;

        public MatchItemDTO(Order o, OrderItem item, Product product, int stock) {
            this.orderNo       = o.getOrderNo();
            this.channelName   = o.getChannel() != null ? o.getChannel().getChannelName() : "";
            this.recipientName = o.getRecipientName();
            this.productName   = item.getProductName();
            this.productCode   = item.getProductCode();
            this.sku           = product != null ? product.getSku() : null;
            this.ordered       = item.getQuantity() != null ? item.getQuantity() : 0;
            this.warehouseStock = stock;
            this.orderedAt     = o.getOrderedAt() != null ? o.getOrderedAt().toString() : "";

            if (product == null) {
                this.allocatable = 0;
                this.shipStatus  = "NOT_MATCHED";
            } else if (stock <= 0) {
                this.allocatable = 0;
                this.shipStatus  = "IMPOSSIBLE";
            } else if (stock >= this.ordered) {
                this.allocatable = this.ordered;
                this.shipStatus  = "FULL";
            } else {
                this.allocatable = stock;
                this.shipStatus  = "PARTIAL";
            }
        }
    }

    public static class MatchResultDTO {
        public String warehouseCode;
        public String warehouseName;
        public int    totalItems;
        public int    full;
        public int    partial;
        public int    impossible;
        public int    notMatched;
        public List<MatchItemDTO> items;

        public MatchResultDTO(String code, String name, List<MatchItemDTO> items) {
            this.warehouseCode = code;
            this.warehouseName = name;
            this.items         = items;
            this.totalItems    = items.size();
            this.full          = (int) items.stream().filter(i -> "FULL".equals(i.shipStatus)).count();
            this.partial       = (int) items.stream().filter(i -> "PARTIAL".equals(i.shipStatus)).count();
            this.impossible    = (int) items.stream().filter(i -> "IMPOSSIBLE".equals(i.shipStatus)).count();
            this.notMatched    = (int) items.stream().filter(i -> "NOT_MATCHED".equals(i.shipStatus)).count();
        }
    }

    /**
     * 할당 창고 기준 재고 매칭
     * GET /api/stock-matching/match?warehouseCode=ANYANG&warehouseName=본사(안양)
     */
    @GetMapping("/match")
    @Transactional(readOnly = true)
    public ResponseEntity<MatchResultDTO> match(
        @RequestParam String warehouseCode,
        @RequestParam(defaultValue = "") String warehouseName,
        @RequestParam(defaultValue = "0")   int page,
        @RequestParam(defaultValue = "200") int size
    ) {
        log.info("재고 매칭: warehouse={}", warehouseCode);

        Pageable pageable = PageRequest.of(page, Math.min(size, 200),
            Sort.by(Sort.Direction.DESC, "orderedAt"));

        List<Order> orders = new ArrayList<>();
        orders.addAll(orderRepository.findByOrderStatus(Order.OrderStatus.PENDING,   pageable).getContent());
        orders.addAll(orderRepository.findByOrderStatus(Order.OrderStatus.CONFIRMED, pageable).getContent());

        orders.forEach(o -> {
            o.getItems().size();
            if (o.getChannel() != null) o.getChannel().getChannelName();
        });

        List<MatchItemDTO> items = new ArrayList<>();
        for (Order o : orders) {
            for (OrderItem item : o.getItems()) {
                Product product = findProduct(item);
                int stock = getWarehouseStock(product, warehouseCode);
                items.add(new MatchItemDTO(o, item, product, stock));
            }
        }

        // 정렬: FULL → PARTIAL → IMPOSSIBLE → NOT_MATCHED
        items.sort(Comparator.comparingInt(i -> switch (i.shipStatus) {
            case "FULL"        -> 0;
            case "PARTIAL"     -> 1;
            case "IMPOSSIBLE"  -> 2;
            default            -> 3;
        }));

        log.info("매칭 완료: 완전:{}, 부분:{}, 불가:{}, 미매칭:{}",
            items.stream().filter(i -> "FULL".equals(i.shipStatus)).count(),
            items.stream().filter(i -> "PARTIAL".equals(i.shipStatus)).count(),
            items.stream().filter(i -> "IMPOSSIBLE".equals(i.shipStatus)).count(),
            items.stream().filter(i -> "NOT_MATCHED".equals(i.shipStatus)).count());

        return ResponseEntity.ok(new MatchResultDTO(warehouseCode, warehouseName, items));
    }

    /**
     * 매칭 결과 기준 재고 예약 (FULL / PARTIAL 만)
     * POST /api/stock-matching/reserve
     * Body: { "warehouseCode": "ANYANG", "orderNos": ["OMS-...", ...] }
     */
    @PostMapping("/reserve")
    @Transactional
    public ResponseEntity<Map<String, Object>> reserve(@RequestBody Map<String, Object> body) {
        String warehouseCode = (String) body.get("warehouseCode");
        @SuppressWarnings("unchecked")
        List<String> orderNos = (List<String>) body.getOrDefault("orderNos", List.of());

        if (warehouseCode == null || warehouseCode.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "창고 코드 필요"));
        }

        log.info("재고 예약: 창고={}, {}건", warehouseCode, orderNos.size());

        int reserved = 0;
        int failed   = 0;
        List<String> failedNos = new ArrayList<>();

        for (String orderNo : orderNos) {
            Order order = orderRepository.findByOrderNo(orderNo).orElse(null);
            if (order == null) { failed++; failedNos.add(orderNo); continue; }

            order.getItems().size();
            boolean ok = true;

            for (OrderItem item : order.getItems()) {
                Product product = findProduct(item);
                if (product == null) { ok = false; continue; }
                int qty = item.getQuantity() != null ? item.getQuantity() : 0;
                int stock = getWarehouseStock(product, warehouseCode);
                int reserveQty = Math.min(qty, stock); // 부분출고도 가능한 만큼 예약
                if (reserveQty <= 0) { ok = false; continue; }
                try {
                    inventoryService.reserveStock(product.getProductId(), reserveQty);
                } catch (Exception e) {
                    log.error("예약 실패: {} - {}", orderNo, e.getMessage());
                    ok = false;
                }
            }

            if (ok) {
                order.setOrderStatus(Order.OrderStatus.CONFIRMED);
                orderRepository.save(order);
                reserved++;
            } else {
                failed++;
                failedNos.add(orderNo);
            }
        }

        return ResponseEntity.ok(Map.of(
            "success",  true,
            "reserved", reserved,
            "failed",   failed,
            "failedOrderNos", failedNos,
            "message",  reserved + "건 재고 예약 완료 (검수발송 시 실차감)"
        ));
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────

    private Product findProduct(OrderItem item) {
        if (item.getProductCode() == null || item.getProductCode().isBlank()) return null;
        List<Product> found = productRepository.searchProducts(item.getProductCode());
        return found.stream()
            .filter(p -> item.getProductCode().equalsIgnoreCase(p.getSku())
                      || item.getProductCode().equalsIgnoreCase(p.getBarcode()))
            .findFirst().orElse(null);
    }

    private int getWarehouseStock(Product product, String warehouseCode) {
        if (product == null) return 0;
        return switch (warehouseCode.toUpperCase()) {
            case "ANYANG"  -> product.getWarehouseStockAnyang();
            case "ICHEON"  -> product.getWarehouseStockIcheon();
            case "BUCHEON" -> product.getWarehouseStockBucheon();
            default        -> product.getAvailableStock();
        };
    }
}
