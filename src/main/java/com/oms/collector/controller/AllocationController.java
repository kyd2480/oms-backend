package com.oms.collector.controller;

import com.oms.collector.entity.Order;
import com.oms.collector.entity.OrderItem;
import com.oms.collector.entity.Product;
import com.oms.collector.repository.OrderRepository;
import com.oms.collector.repository.ProductRepository;
import com.oms.collector.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 재고 할당 컨트롤러
 *
 * GET  /api/allocation/preview          - 할당 미리보기
 * POST /api/allocation/allocate          - 재고 예약 (차감 아님)
 * POST /api/allocation/release/{orderNo} - 할당 취소 (예약 해제)
 * POST /api/allocation/confirm/{orderNo} - 검수발송 시 실제 차감 (검수발송 컨트롤러에서 호출)
 */
@Slf4j
@RestController
@RequestMapping("/api/allocation")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AllocationController {

    private final OrderRepository     orderRepository;
    private final ProductRepository   productRepository;
    private final InventoryService    inventoryService;

    // ─── DTO ─────────────────────────────────────────────────────

    public static class AllocationItemDTO {
        public String  orderNo;
        public String  channelName;
        public String  recipientName;
        public String  productName;
        public String  productCode;   // OrderItem.productCode
        public String  sku;
        public int     required;      // 주문 수량
        public Integer currentStock;  // 현재 재고 (null = 상품 미매칭)
        public boolean allocatable;   // 할당 가능 여부
        public String  status;        // READY / NO_STOCK / NOT_MATCHED / ALREADY_ALLOCATED
        public String  orderedAt;

        public AllocationItemDTO(Order o, OrderItem item, Product product) {
            this.orderNo      = o.getOrderNo();
            this.channelName  = o.getChannel() != null ? o.getChannel().getChannelName() : "";
            this.recipientName = o.getRecipientName();
            this.productName  = item.getProductName();
            this.productCode  = item.getProductCode();
            this.sku          = product != null ? product.getSku() : null;
            this.required     = item.getQuantity() != null ? item.getQuantity() : 0;
            this.currentStock = product != null ? product.getAvailableStock() : null;
            this.orderedAt    = o.getOrderedAt() != null ? o.getOrderedAt().toString() : "";

            if (product == null) {
                this.allocatable = false;
                this.status      = "NOT_MATCHED";
            } else if ("ALLOCATED".equals(o.getOrderStatus().name())) {
                this.allocatable = false;
                this.status      = "ALREADY_ALLOCATED";
            } else if (product.getAvailableStock() < this.required) {
                this.allocatable = false;
                this.status      = "NO_STOCK";
            } else {
                this.allocatable = true;
                this.status      = "READY";
            }
        }
    }

    public static class PreviewResultDTO {
        public int total;
        public int allocatable;
        public int noStock;
        public int notMatched;
        public List<AllocationItemDTO> items;

        public PreviewResultDTO(List<AllocationItemDTO> items) {
            this.items        = items;
            this.total        = items.size();
            this.allocatable  = (int) items.stream().filter(i -> "READY".equals(i.status)).count();
            this.noStock      = (int) items.stream().filter(i -> "NO_STOCK".equals(i.status)).count();
            this.notMatched   = (int) items.stream().filter(i -> "NOT_MATCHED".equals(i.status)).count();
        }
    }

    /**
     * 할당 미리보기
     * GET /api/allocation/preview
     */
    @GetMapping("/preview")
    @Transactional(readOnly = true)
    public ResponseEntity<PreviewResultDTO> preview() {
        log.info("재고 할당 미리보기");

        // PENDING / CONFIRMED 상태 주문만 직접 조회 (findAll 대신)
        List<Order> orders = new ArrayList<>();
        orders.addAll(orderRepository.findByOrderStatusOrderByOrderedAtDesc(Order.OrderStatus.PENDING));
        orders.addAll(orderRepository.findByOrderStatusOrderByOrderedAtDesc(Order.OrderStatus.CONFIRMED));

        orders.forEach(o -> {
            o.getItems().size();
            if (o.getChannel() != null) o.getChannel().getChannelName();
        });

        List<AllocationItemDTO> items = new ArrayList<>();
        for (Order o : orders) {
            for (OrderItem item : o.getItems()) {
                Product product = findProduct(item);
                items.add(new AllocationItemDTO(o, item, product));
            }
        }

        // READY 먼저, 그 다음 NO_STOCK, NOT_MATCHED 순
        items.sort(Comparator.comparing((AllocationItemDTO i) -> {
            return switch (i.status) {
                case "READY"             -> 0;
                case "NO_STOCK"          -> 1;
                case "NOT_MATCHED"       -> 2;
                case "ALREADY_ALLOCATED" -> 3;
                default                  -> 4;
            };
        }));

        log.info("미리보기 완료: 전체 {}건, 할당가능 {}건", items.size(), 
            items.stream().filter(i -> "READY".equals(i.status)).count());
        return ResponseEntity.ok(new PreviewResultDTO(items));
    }

    /**
     * 재고 차감 (할당 실행)
     * POST /api/allocation/allocate
     * Body: { "orderNos": ["OMS-...", ...] }  // 빈 배열이면 전체 할당 가능 주문
     */
    @PostMapping("/allocate")
    @Transactional
    public ResponseEntity<Map<String, Object>> allocate(
        @RequestBody Map<String, Object> body
    ) {
        @SuppressWarnings("unchecked")
        List<String> targetOrderNos = (List<String>) body.getOrDefault("orderNos", List.of());
        boolean allocateAll = targetOrderNos.isEmpty();

        log.info("재고 할당 실행: {}", allocateAll ? "전체" : targetOrderNos.size() + "건");

        List<Order> orders;
        if (allocateAll) {
            orders = new ArrayList<>();
            orders.addAll(orderRepository.findByOrderStatusOrderByOrderedAtDesc(Order.OrderStatus.PENDING));
            orders.addAll(orderRepository.findByOrderStatusOrderByOrderedAtDesc(Order.OrderStatus.CONFIRMED));
        } else {
            orders = targetOrderNos.stream()
                .map(no -> orderRepository.findByOrderNo(no).orElse(null))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
        }

        orders.forEach(o -> o.getItems().size());

        int allocated = 0;
        int failed    = 0;
        List<String> failedOrderNos = new ArrayList<>();

        for (Order o : orders) {
            boolean orderOk = true;
            for (OrderItem item : o.getItems()) {
                Product product = findProduct(item);
                if (product == null) { orderOk = false; continue; }
                int qty = item.getQuantity() != null ? item.getQuantity() : 0;
                if (product.getAvailableStock() < qty) { orderOk = false; continue; }

                try {
                    // 재고 예약 (차감 아님 - 실제 차감은 검수발송 시)
                    inventoryService.reserveStock(product.getProductId(), qty);
                } catch (Exception e) {
                    log.error("재고 예약 실패: {} - {}", o.getOrderNo(), e.getMessage());
                    orderOk = false;
                }
            }

            if (orderOk && !o.getItems().isEmpty()) {
                o.setOrderStatus(Order.OrderStatus.CONFIRMED);
                orderRepository.save(o);
                allocated++;
            } else {
                failed++;
                failedOrderNos.add(o.getOrderNo());
            }
        }

        log.info("재고 할당 완료: {}건 성공, {}건 실패", allocated, failed);
        return ResponseEntity.ok(Map.of(
            "success",   true,
            "allocated", allocated,
            "failed",    failed,
            "failedOrderNos", failedOrderNos,
            "message",   allocated + "건 재고 예약 완료 (검수발송 시 실제 차감)" + (failed > 0 ? " (" + failed + "건 실패)" : "")
        ));
    }

    /**
     * 할당 취소 (재고 복구)
     * POST /api/allocation/release/{orderNo}
     */
    @PostMapping("/release/{orderNo}")
    @Transactional
    public ResponseEntity<Map<String, Object>> release(@PathVariable String orderNo) {
        log.info("재고 할당 취소: {}", orderNo);

        Order order = orderRepository.findByOrderNo(orderNo)
            .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다: " + orderNo));

        order.getItems().size();

        for (OrderItem item : order.getItems()) {
            Product product = findProduct(item);
            if (product == null) continue;
            int qty = item.getQuantity() != null ? item.getQuantity() : 0;
            try {
                // 예약 해제 (차감된 게 아니므로 예약량만 복구)
                inventoryService.releaseReservedStock(product.getProductId(), qty);
            } catch (Exception e) {
                log.error("재고 예약 해제 실패: {}", e.getMessage());
            }
        }

        order.setOrderStatus(Order.OrderStatus.PENDING);
        orderRepository.save(order);

        return ResponseEntity.ok(Map.of("success", true, "message", "할당 취소 완료, 재고 복구됨"));
    }

    // ─── 상품 매칭 (productCode → SKU → 이름 순서로 조회) ────────
    private Product findProduct(OrderItem item) {
        // 1) productCode로 조회
        if (item.getProductCode() != null && !item.getProductCode().isBlank()) {
            List<Product> found = productRepository.searchProducts(item.getProductCode());
            if (!found.isEmpty()) return found.get(0);
        }
        // 2) 상품명으로 조회
        if (item.getProductName() != null && !item.getProductName().isBlank()) {
            List<Product> found = productRepository.searchProducts(item.getProductName());
            if (!found.isEmpty()) return found.get(0);
        }
        return null;
    }

    /**
     * 검수발송 시 실제 재고 차감
     * POST /api/allocation/confirm/{orderNo}
     * (검수발송 컨트롤러에서 호출)
     */
    @PostMapping("/confirm/{orderNo}")
    @Transactional
    public ResponseEntity<Map<String, Object>> confirmAndDeduct(@PathVariable String orderNo) {
        log.info("재고 실차감 (검수발송): {}", orderNo);

        Order order = orderRepository.findByOrderNo(orderNo)
            .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다: " + orderNo));

        order.getItems().size();

        for (OrderItem item : order.getItems()) {
            Product product = findProduct(item);
            if (product == null) continue;
            int qty = item.getQuantity() != null ? item.getQuantity() : 0;
            try {
                // 예약 해제 후 실제 차감
                inventoryService.releaseReservedStock(product.getProductId(), qty);
                inventoryService.processOutboundWithWarehouse(
                    product.getProductId(), qty, "AUTO", order.getOrderId(),
                    "검수발송 출고: " + orderNo
                );
            } catch (Exception e) {
                log.error("재고 실차감 실패: {} - {}", orderNo, e.getMessage());
            }
        }

        order.setOrderStatus(Order.OrderStatus.SHIPPED);
        orderRepository.save(order);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "재고 차감 및 발송 처리 완료: " + orderNo
        ));
    }

}