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

        // Projection 기반 생성자 (코드 매칭 성공 - product 정보 포함)
        public MatchItemDTO(com.oms.collector.repository.MatchedItemProjection row, int stock) {
            this.orderNo        = row.getOrderNo();
            this.channelName    = row.getChannelName() != null ? row.getChannelName() : "";
            this.recipientName  = row.getRecipientName();
            this.productName    = row.getProductName();
            this.productCode    = row.getProductCode();
            this.sku            = row.getSku();
            this.ordered        = row.getQuantity() != null ? row.getQuantity() : 0;
            this.warehouseStock = stock;
            this.orderedAt      = row.getOrderedAt() != null ? row.getOrderedAt() : "";

            if (row.getProductId() == null) {
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

        // Projection 기반 생성자 (상품명 매칭 - product 별도 전달)
        public MatchItemDTO(com.oms.collector.repository.MatchedItemProjection row, Product product, int stock) {
            this.orderNo        = row.getOrderNo();
            this.channelName    = row.getChannelName() != null ? row.getChannelName() : "";
            this.recipientName  = row.getRecipientName();
            this.productName    = row.getProductName();
            this.productCode    = row.getProductCode();
            this.sku            = product != null ? product.getSku() : null;
            this.ordered        = row.getQuantity() != null ? row.getQuantity() : 0;
            this.warehouseStock = stock;
            this.orderedAt      = row.getOrderedAt() != null ? row.getOrderedAt() : "";

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
     *
     * 개선: 9551건 Java 루프 → DB JOIN 쿼리 2번으로 교체
     *   1) 코드 매칭 성공 아이템: SQL JOIN으로 한 번에 처리
     *   2) 코드 매칭 실패 아이템: 상품명 Jaccard (소수만)
     */
    @GetMapping("/match")
    @Transactional(readOnly = true)
    public ResponseEntity<MatchResultDTO> match(
        @RequestParam String warehouseCode,
        @RequestParam(defaultValue = "") String warehouseName,
        @RequestParam(defaultValue = "0")   int page,
        @RequestParam(defaultValue = "200") int size
    ) {
        log.info("재고 매칭 시작: warehouse={}", warehouseCode);
        long t0 = System.currentTimeMillis();

        // ── 신규 창고 재고 캐시 (ANYANG/ICHEON/BUCHEON 외) ────────
        Map<UUID, Integer> warehouseStockCache = new HashMap<>();
        warehouseStockRepository.findByWarehouseCode(warehouseCode)
            .forEach(ws -> warehouseStockCache.put(ws.getProductId(), ws.getStock()));

        List<MatchItemDTO> items = new ArrayList<>();

        // ── 1단계: 코드 매칭 성공 → DB JOIN (Java 루프 없음) ──────
        List<com.oms.collector.repository.MatchedItemProjection> matched =
            orderRepository.findPendingMatchedByCode();
        log.info("[PERF] DB JOIN 코드매칭: {}건 → {}ms", matched.size(), System.currentTimeMillis() - t0);

        for (var row : matched) {
            int stock = getStockFromProjection(row, warehouseCode, warehouseStockCache);
            items.add(new MatchItemDTO(row, stock));
        }

        // ── 2단계: 코드 매칭 실패 → 상품명 Jaccard (소수만) ───────
        long t1 = System.currentTimeMillis();
        List<com.oms.collector.repository.MatchedItemProjection> unmatched =
            orderRepository.findPendingUnmatchedByCode();
        log.info("[PERF] 미매칭 조회: {}건 → {}ms", unmatched.size(), System.currentTimeMillis() - t1);

        if (!unmatched.isEmpty()) {
            long t2 = System.currentTimeMillis();
            List<Product> allProducts = productRepository.findAll();
            Map<String, Product> skuMap     = new HashMap<>();
            Map<String, Product> barcodeMap = new HashMap<>();
            allProducts.forEach(prod -> {
                if (prod.getSku()     != null) skuMap.put(prod.getSku().toLowerCase(), prod);
                if (prod.getBarcode() != null) barcodeMap.put(prod.getBarcode().toLowerCase(), prod);
            });

            for (var row : unmatched) {
                Product product = findBestMatchByName(row.getProductName(), skuMap, barcodeMap, allProducts);
                int stock = product != null
                    ? getWarehouseStockCached(product, warehouseCode, warehouseStockCache)
                    : 0;
                items.add(new MatchItemDTO(row, product, stock));
            }
            log.info("[PERF] 상품명 매칭 완료: {}건 → {}ms", unmatched.size(), System.currentTimeMillis() - t2);
        }

        // 정렬: FULL → PARTIAL → IMPOSSIBLE → NOT_MATCHED
        items.sort(Comparator.comparingInt(i -> switch (i.shipStatus) {
            case "FULL"        -> 0;
            case "PARTIAL"     -> 1;
            case "IMPOSSIBLE"  -> 2;
            default            -> 3;
        }));

        log.info("[PERF] 전체 완료: {}건 → {}ms", items.size(), System.currentTimeMillis() - t0);
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

        // 캐시 1회 로딩
        List<Product> allProducts = productRepository.findAll();
        Map<String, Product> skuMap     = new HashMap<>();
        Map<String, Product> barcodeMap = new HashMap<>();
        allProducts.forEach(prod -> {
            if (prod.getSku()     != null) skuMap.put(prod.getSku().toLowerCase(), prod);
            if (prod.getBarcode() != null) barcodeMap.put(prod.getBarcode().toLowerCase(), prod);
        });

        int reserved = 0, failed = 0;
        List<String> failedNos = new ArrayList<>();

        for (String orderNo : orderNos) {
            Order order = orderRepository.findByOrderNo(orderNo).orElse(null);
            if (order == null) { failed++; failedNos.add(orderNo); continue; }
            order.getItems().size();
            boolean ok = true;

            for (OrderItem item : order.getItems()) {
                Product product = findProductFromCache(item, skuMap, barcodeMap, allProducts);
                if (product == null) { ok = false; continue; }
                int qty = item.getQuantity() != null ? item.getQuantity() : 0;
                int stock = getWarehouseStock(product, warehouseCode);
                int reserveQty = Math.min(qty, stock);
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

    /**
     * Projection에서 창고 코드에 맞는 재고 반환
     */
    private int getStockFromProjection(
            com.oms.collector.repository.MatchedItemProjection row,
            String warehouseCode,
            Map<UUID, Integer> cache) {
        return switch (warehouseCode.toUpperCase()) {
            case "ANYANG"     -> row.getWarehouseStockAnyang()  != null ? row.getWarehouseStockAnyang()  : 0;
            case "ICHEON_BOX",
                 "ICHEON_PCS" -> row.getWarehouseStockIcheon()  != null ? row.getWarehouseStockIcheon()  : 0;
            case "BUCHEON"    -> row.getWarehouseStockBucheon() != null ? row.getWarehouseStockBucheon() : 0;
            default           -> row.getProductId() != null ? cache.getOrDefault(row.getProductId(), 0) : 0;
        };
    }

    /**
     * 상품명 기반 매칭 — 코드 매칭 실패한 소수 아이템용
     */
    private Product findBestMatchByName(String productName,
                                        Map<String, Product> skuMap,
                                        Map<String, Product> barcodeMap,
                                        List<Product> allProducts) {
        if (productName == null || productName.isBlank()) return null;

        String firstToken = productName.split("[ /\\\\|·•\\-_]+")[0].toLowerCase();
        if (firstToken.isBlank()) return null;

        List<Product> candidates = new ArrayList<>();
        for (Map.Entry<String, Product> e : skuMap.entrySet()) {
            if (e.getKey().startsWith(firstToken)) candidates.add(e.getValue());
        }
        if (candidates.isEmpty()) {
            for (Map.Entry<String, Product> e : barcodeMap.entrySet()) {
                if (e.getKey().startsWith(firstToken)) candidates.add(e.getValue());
            }
        }
        if (candidates.isEmpty()) {
            for (Product p : allProducts) {
                if (p.getProductName() != null &&
                        p.getProductName().toLowerCase().contains(firstToken)) {
                    candidates.add(p);
                }
            }
        }
        if (candidates.isEmpty()) return null;

        final String pName = productName;
        return candidates.stream()
            .max(Comparator.comparingDouble(p -> jaccardSimilarity(pName, p.getProductName())))
            .filter(p -> jaccardSimilarity(pName, p.getProductName()) >= 0.3)
            .orElse(null);
    }


    private boolean isChannelProductCode(String code) {
        if (code == null) return false;
        return code.matches("(?i)(11ST|NAVER|CP|GS|COUPANG|KAKAO)-.*");
    }

    /**
     * 캐시 기반 상품 조회 (DB 조회 없음)
     */
    private Product findProductFromCache(OrderItem item,
                                          Map<String, Product> skuMap,
                                          Map<String, Product> barcodeMap,
                                          List<Product> allProducts) {
        String code = item.getProductCode();

        if (code != null && !code.isBlank() && !isChannelProductCode(code)) {
            String lower = code.toLowerCase();
            Product exact = skuMap.containsKey(lower) ? skuMap.get(lower)
                          : barcodeMap.get(lower);
            if (exact != null) return exact;
        }
        // SKU/바코드 매칭 실패 시 상품명 인덱스 기반 조회
        return findBestMatchByNameFromCache(item.getProductName(), skuMap, barcodeMap, allProducts);
    }

    /**
     * 상품명 기반 매칭 — 전체 스캔 제거
     *
     * 기존: 후보 없으면 allProducts 전체 Jaccard → O(N*아이템수) → 1.5분 원인
     * 개선: 첫 토큰으로 SKU/바코드맵에서 prefix 매칭 → O(후보수) ≈ O(수십)
     *       후보가 없으면 null 반환 (전체 스캔 금지)
     */
    private Product findBestMatchByNameFromCache(String productName,
                                                  Map<String, Product> skuMap,
                                                  Map<String, Product> barcodeMap,
                                                  List<Product> allProducts) {
        if (productName == null || productName.isBlank()) return null;
        if (allProducts.isEmpty()) return null;

        // 첫 토큰 추출 (보통 SKU 앞부분 코드)
        String firstToken = productName.split("[ /\\\\|·•\\-_]+")[0].toLowerCase();
        if (firstToken.isBlank()) return null;

        // 1단계: SKU prefix 매칭
        List<Product> candidates = new ArrayList<>();
        for (Map.Entry<String, Product> e : skuMap.entrySet()) {
            if (e.getKey().startsWith(firstToken)) candidates.add(e.getValue());
        }
        // 2단계: 바코드 prefix 매칭
        if (candidates.isEmpty()) {
            for (Map.Entry<String, Product> e : barcodeMap.entrySet()) {
                if (e.getKey().startsWith(firstToken)) candidates.add(e.getValue());
            }
        }
        // 3단계: 상품명 contains — 여전히 후보 없을 때만 (전체 스캔이지만 발생 빈도 낮음)
        if (candidates.isEmpty()) {
            for (Product p : allProducts) {
                if (p.getProductName() != null &&
                        p.getProductName().toLowerCase().contains(firstToken)) {
                    candidates.add(p);
                }
            }
        }
        // 그래도 없으면 매칭 불가 반환 (allProducts 전체 Jaccard 스캔 금지)
        if (candidates.isEmpty()) return null;

        final String pName = productName;
        return candidates.stream()
            .max(Comparator.comparingDouble(p -> jaccardSimilarity(pName, p.getProductName())))
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
                yield warehouseStockRepository
                    .findByProductIdAndWarehouseCode(product.getProductId(), warehouseCode)
                    .map(com.oms.collector.entity.ProductWarehouseStock::getStock)
                    .orElse(0);
            }
        };
    }

    // 캐시 기반 창고 재고 조회 (N+1 완전 제거)
    private int getWarehouseStockCached(Product product, String warehouseCode,
                                        Map<UUID, Integer> cache) {
        if (product == null) return 0;
        return switch (warehouseCode.toUpperCase()) {
            case "ANYANG"     -> product.getWarehouseStockAnyang()  != null ? product.getWarehouseStockAnyang()  : 0;
            case "ICHEON_BOX",
                 "ICHEON_PCS" -> product.getWarehouseStockIcheon()  != null ? product.getWarehouseStockIcheon()  : 0;
            case "BUCHEON"    -> product.getWarehouseStockBucheon() != null ? product.getWarehouseStockBucheon() : 0;
            default           -> cache.getOrDefault(product.getProductId(), 0);
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

        Map<UUID, Integer> warehouseStockCache = new HashMap<>();
        if (!warehouseCode.isBlank()) {
            warehouseStockRepository.findByWarehouseCode(warehouseCode)
                .forEach(ws -> warehouseStockCache.put(ws.getProductId(), ws.getStock()));
        }

        List<MatchItemDTO> items = orderRepository.findConfirmedWithProducts().stream()
            .map(row -> {
                int stock = warehouseCode.isBlank() ? 0
                    : getStockFromProjection(row, warehouseCode, warehouseStockCache);
                MatchItemDTO dto = new MatchItemDTO(row, stock);
                dto.shipStatus = "ALLOCATED";
                return dto;
            })
            .collect(Collectors.toList());

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

        // 이 주문만 필요하므로 전체 캐시 대신 단건 SKU/바코드 조회
        List<Product> allProducts = productRepository.findAll();
        Map<String, Product> skuMap     = new HashMap<>();
        Map<String, Product> barcodeMap = new HashMap<>();
        allProducts.forEach(prod -> {
            if (prod.getSku()     != null) skuMap.put(prod.getSku().toLowerCase(), prod);
            if (prod.getBarcode() != null) barcodeMap.put(prod.getBarcode().toLowerCase(), prod);
        });

        for (OrderItem item : order.getItems()) {
            Product product = findProductFromCache(item, skuMap, barcodeMap, allProducts);
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
