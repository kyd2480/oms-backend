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
    private final com.oms.collector.repository.ProductWarehouseStockRepository warehouseStockRepository;

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
        public String address;
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
        public int    totalOrders;
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
            this.totalOrders   = (int) items.stream().map(i -> i.orderNo).distinct().count();
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

        List<Order> orders = new ArrayList<>();
        // 매칭 탭 = PENDING만 (CONFIRMED는 할당완료 탭에서 별도 조회)
        // 전체 조회 (페이지네이션 제거 - 건수 제한 없이 전체 처리)
        int p = 0;
        while (true) {
            Pageable pageable = PageRequest.of(p++, 500, Sort.by(Sort.Direction.DESC, "orderedAt"));
            var slice = orderRepository.findByOrderStatus(Order.OrderStatus.PENDING, pageable);
            orders.addAll(slice.getContent());
            if (!slice.hasNext()) break;
        }

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

    // 쇼핑몰 상품코드 패턴 (11ST-PRD-xxx, NAVER-PRD-xxx, CP-PRD-xxx 등)
    private boolean isChannelProductCode(String code) {
        if (code == null) return false;
        return code.matches("(?i)(11ST|NAVER|CP|GS|COUPANG|KAKAO)-.*");
    }

    private Product findProduct(OrderItem item) {
        String code = item.getProductCode();

        // productCode가 없거나 쇼핑몰 상품코드면 → 상품명으로 유사도 검색
        if (code == null || code.isBlank() || isChannelProductCode(code)) {
            return findBestMatchByName(item.getProductName());
        }

        // 바코드/SKU로 직접 검색
        List<Product> found = productRepository.searchProducts(code);
        Product exact = found.stream()
            .filter(p -> code.equalsIgnoreCase(p.getSku())
                      || code.equalsIgnoreCase(p.getBarcode()))
            .findFirst().orElse(null);

        // 정확히 못 찾으면 상품명으로 fallback
        return exact != null ? exact : findBestMatchByName(item.getProductName());
    }

    private Product findBestMatchByName(String productName) {
        if (productName == null || productName.isBlank()) return null;

        // 상품코드 부분 추출 (첫 토큰 - 공백 전)
        String keyword = productName.split(" ")[0];
        if (keyword.length() > 12) keyword = keyword.substring(0, 12);

        List<Product> candidates = productRepository.searchProducts(keyword);

        // 후보 없으면 앞 8자로 재검색
        if (candidates.isEmpty()) {
            String fallback = productName.length() > 8 ? productName.substring(0, 8) : productName;
            candidates = productRepository.searchProducts(fallback);
        }

        if (candidates.isEmpty()) return null;

        // Jaccard 유사도로 최적 선택 (0.3 이상)
        final String pName = productName;
        return candidates.stream()
            .max(java.util.Comparator.comparingDouble(p -> jaccardSimilarity(pName, p.getProductName())))
            .filter(p -> jaccardSimilarity(pName, p.getProductName()) >= 0.3)
            .orElse(null);
    }

    private double jaccardSimilarity(String a, String b) {
        if (a == null || b == null) return 0.0;
        Set<String> ta = tokenize(a);
        Set<String> tb = tokenize(b);
        if (ta.isEmpty() || tb.isEmpty()) return 0.0;
        Set<String> inter = new HashSet<>(ta);
        inter.retainAll(tb);
        Set<String> union = new HashSet<>(ta);
        union.addAll(tb);
        return (double) inter.size() / union.size();
    }

    private Set<String> tokenize(String s) {
        String n = s.replaceAll("[/\\\\|·•\\-_]", " ").replaceAll("\\s+", " ").trim().toLowerCase();
        return new HashSet<>(java.util.Arrays.asList(n.split(" ")));
    }

    private int getWarehouseStock(Product product, String warehouseCode) {
        if (product == null) return 0;
        return switch (warehouseCode.toUpperCase()) {
            case "ANYANG"     -> product.getWarehouseStockAnyang();
            case "ICHEON_BOX",
                 "ICHEON_PCS" -> product.getWarehouseStockIcheon();
            case "BUCHEON"    -> product.getWarehouseStockBucheon();
            default           -> {
                // 신규 창고: product_warehouse_stock 테이블에서 조회
                yield warehouseStockRepository
                    .findByProductIdAndWarehouseCode(product.getProductId(), warehouseCode)
                    .map(com.oms.collector.entity.ProductWarehouseStock::getStock)
                    .orElse(0);
            }
        };
    }

    /**
     * 할당 완료 목록 조회 (CONFIRMED 상태)
     * GET /api/stock-matching/allocated?warehouseCode=ANYANG
     */
    @GetMapping("/allocated")
    @Transactional(readOnly = true)
    public ResponseEntity<List<MatchItemDTO>> getAllocated(
        @RequestParam(defaultValue = "")    String warehouseCode,
        @RequestParam(defaultValue = "0")   int page,
        @RequestParam(defaultValue = "200") int size
    ) {
        log.info("할당 완료 목록 조회: warehouse={}", warehouseCode);

        List<Order> orders = new ArrayList<>();
        int p2 = 0;
        while (true) {
            Pageable pageable = PageRequest.of(p2++, 500, Sort.by(Sort.Direction.DESC, "orderedAt"));
            var slice = orderRepository.findByOrderStatus(Order.OrderStatus.CONFIRMED, pageable);
            orders.addAll(slice.getContent());
            if (!slice.hasNext()) break;
        }

        orders.forEach(o -> {
            o.getItems().size();
            if (o.getChannel() != null) o.getChannel().getChannelName();
        });

        List<MatchItemDTO> items = new ArrayList<>();
        for (Order o : orders) {
            for (OrderItem item : o.getItems()) {
                Product product = findProduct(item);
                int stock = warehouseCode.isBlank() ? 0 : getWarehouseStock(product, warehouseCode);
                MatchItemDTO dto = new MatchItemDTO(o, item, product, stock);
                dto.shipStatus = "ALLOCATED"; // 할당완료 상태 표시
                items.add(dto);
            }
        }

        return ResponseEntity.ok(items);
    }

    /**
     * 할당 취소 (CONFIRMED → PENDING, 예약 해제)
     * POST /api/stock-matching/cancel-reserve/{orderNo}
     */
    @PostMapping("/cancel-reserve/{orderNo}")
    @Transactional
    public ResponseEntity<Map<String, Object>> cancelReserve(@PathVariable String orderNo) {
        log.info("할당 취소: {}", orderNo);

        Order order = orderRepository.findByOrderNo(orderNo)
            .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다: " + orderNo));

        order.getItems().size();

        for (OrderItem item : order.getItems()) {
            Product product = findProduct(item);
            if (product == null) continue;
            int qty = item.getQuantity() != null ? item.getQuantity() : 0;
            try {
                inventoryService.releaseReservedStock(product.getProductId(), qty);
            } catch (Exception e) {
                log.warn("예약 해제 실패 (무시): {} - {}", orderNo, e.getMessage());
            }
        }

        order.setOrderStatus(Order.OrderStatus.PENDING);
        orderRepository.save(order);

        return ResponseEntity.ok(Map.of("success", true, "message", "할당 취소 완료: " + orderNo));
    }

}