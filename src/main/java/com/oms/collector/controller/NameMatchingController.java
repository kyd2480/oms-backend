package com.oms.collector.controller;

import com.oms.collector.entity.Order;
import com.oms.collector.entity.OrderItem;
import com.oms.collector.entity.Product;
import com.oms.collector.entity.ProductMatchingRule;
import com.oms.collector.repository.OrderRepository;
import com.oms.collector.repository.ProductMatchingRuleRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.oms.collector.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 상품명 매칭 컨트롤러
 *
 * GET  /api/matching/unmatched        - 미매칭 주문상품 목록
 * GET  /api/matching/search?keyword=  - 재고 상품 검색
 * POST /api/matching/match            - 매칭 확정 (룰 저장 + OrderItem 업데이트)
 * POST /api/matching/auto             - 자동매칭 실행 (기존 룰 + 유사도)
 * GET  /api/matching/rules            - 저장된 매칭 룰 목록
 * DELETE /api/matching/rules/{id}     - 룰 삭제
 */
@Slf4j
@RestController
@RequestMapping("/api/matching")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class NameMatchingController {

    private final OrderRepository             orderRepository;
    private final ProductRepository           productRepository;
    private final ProductMatchingRuleRepository ruleRepository;

    // ─── DTO ─────────────────────────────────────────────────────

    public static class UnmatchedItemDTO {
        public String orderNo;
        public String itemId;
        public String channelName;
        public String recipientName;
        public String channelProductName; // 쇼핑몰 상품명
        public String optionName;
        public String productCode;
        public int    quantity;
        public String suggestedProductId;   // 자동 추천 상품 ID
        public String suggestedProductName; // 자동 추천 상품명
        public String suggestedSku;
        public String matchStatus; // UNMATCHED / AUTO_SUGGESTED / MATCHED

        public UnmatchedItemDTO(Order o, OrderItem item) {
            this.orderNo             = o.getOrderNo();
            this.itemId              = item.getItemId() != null ? item.getItemId().toString() : "";
            this.channelName         = o.getChannel() != null ? o.getChannel().getChannelName() : "";
            this.recipientName       = o.getRecipientName();
            this.channelProductName  = item.getProductName();
            this.optionName          = item.getOptionName();
            this.productCode         = item.getProductCode();
            this.quantity            = item.getQuantity() != null ? item.getQuantity() : 0;
            this.matchStatus         = "UNMATCHED";
        }
    }

    public static class ProductCandidateDTO {
        public String productId;
        public String productName;
        public String sku;
        public String barcode;
        public int    availableStock;
        public int    totalStock;

        public ProductCandidateDTO(Product p) {
            this.productId      = p.getProductId().toString();
            this.productName    = p.getProductName();
            this.sku            = p.getSku();
            this.barcode        = p.getBarcode();
            this.availableStock = p.getAvailableStock();
            this.totalStock     = p.getTotalStock();
        }
    }

    /**
     * 미매칭 주문상품 목록
     * GET /api/matching/unmatched
     */
    @GetMapping("/unmatched")
    @Transactional(readOnly = true)
    public ResponseEntity<List<UnmatchedItemDTO>> getUnmatched(
        @RequestParam(defaultValue = "0")   int page,
        @RequestParam(defaultValue = "200") int size
    ) {
        log.info("미매칭 주문상품 조회 (page={}, size={})", page, size);

        List<Order> orders = new ArrayList<>();
        for (Order.OrderStatus st : new Order.OrderStatus[]{Order.OrderStatus.PENDING, Order.OrderStatus.CONFIRMED}) {
            int p = 0; while(true) {
                var pg = PageRequest.of(p++, 500, Sort.by(Sort.Direction.DESC, "orderedAt"));
                var sl = orderRepository.findByOrderStatus(st, pg);
                orders.addAll(sl.getContent()); if(!sl.hasNext()) break;
            }
        }
        orders.forEach(o -> {
            o.getItems().size();
            if (o.getChannel() != null) o.getChannel().getChannelName();
        });

        // ── 캐시 사전 로딩 (N+1 방지) ──────────────────────────
        // 매칭 룰 캐시
        Map<String, ProductMatchingRule> ruleCache = new HashMap<>();
        ruleRepository.findAllByOrderByCreatedAtDesc()
            .forEach(r -> ruleCache.put(r.getChannelProductName(), r));

        // 전체 상품 SKU·바코드 Set (아이템마다 DB 조회 → 메모리 룩업으로 변경)
        Set<String> productSkuSet     = new HashSet<>();
        Set<String> productBarcodeSet = new HashSet<>();
        Map<String, Product> skuToProduct     = new HashMap<>();
        Map<String, Product> barcodeToProduct = new HashMap<>();
        productRepository.findAll().forEach(p -> {
            if (p.getSku()     != null && !p.getSku().isBlank()) {
                productSkuSet.add(p.getSku().toLowerCase());
                skuToProduct.put(p.getSku().toLowerCase(), p);
            }
            if (p.getBarcode() != null && !p.getBarcode().isBlank()) {
                productBarcodeSet.add(p.getBarcode().toLowerCase());
                barcodeToProduct.put(p.getBarcode().toLowerCase(), p);
            }
        });
        // ────────────────────────────────────────────────────────

        List<UnmatchedItemDTO> result = new ArrayList<>();

        for (Order o : orders) {
            for (OrderItem item : o.getItems()) {
                // 룰 캐시 또는 SKU/바코드 Set으로 매칭 여부 확인 (DB 조회 없음)
                if (isMatchedFast(item, ruleCache, productSkuSet, productBarcodeSet)) continue;

                UnmatchedItemDTO dto = new UnmatchedItemDTO(o, item);

                // [1] 바코드(productCode) 직접 추천 — Set 룩업
                if (item.getProductCode() != null && !item.getProductCode().isBlank()) {
                    String code = item.getProductCode().toLowerCase();
                    Product exact = skuToProduct.containsKey(code) ? skuToProduct.get(code)
                                  : barcodeToProduct.get(code);
                    if (exact != null) {
                        dto.suggestedProductId   = exact.getProductId().toString();
                        dto.suggestedProductName = exact.getProductName();
                        dto.suggestedSku         = exact.getSku();
                        dto.matchStatus          = "AUTO_SUGGESTED";
                    }
                }
                // [2] 룰 캐시에서 추천
                if (!"AUTO_SUGGESTED".equals(dto.matchStatus)) {
                    ProductMatchingRule rule = ruleCache.get(item.getProductName());
                    if (rule != null) {
                        dto.suggestedProductId   = rule.getProductId().toString();
                        dto.suggestedProductName = rule.getProductName();
                        dto.suggestedSku         = rule.getSku();
                        dto.matchStatus          = "AUTO_SUGGESTED";
                    }
                }
                // [3] 유사도 추천 (바코드/룰 모두 실패 시만) — 이미 로딩된 상품 목록 재사용
                if (!"AUTO_SUGGESTED".equals(dto.matchStatus)) {
                    Product similar = findBestMatchFromCache(item.getProductName(),
                        new ArrayList<>(skuToProduct.values()));
                    if (similar != null) {
                        dto.suggestedProductId   = similar.getProductId().toString();
                        dto.suggestedProductName = similar.getProductName();
                        dto.suggestedSku         = similar.getSku();
                        dto.matchStatus          = "AUTO_SUGGESTED";
                    }
                }

                result.add(dto);
            }
        }

        log.info("미매칭 항목: {}건", result.size());
        return ResponseEntity.ok(result);
    }

    // 쇼핑몰 상품코드 패턴 감지
    private boolean isChannelProductCode(String code) {
        if (code == null) return false;
        return code.matches("(?i)(11ST|NAVER|CP|GS|COUPANG|KAKAO)-.*");
    }

    // Set 룩업 기반 빠른 매칭 확인 (DB 조회 없음)
    private boolean isMatchedFast(OrderItem item,
                                   Map<String, ProductMatchingRule> ruleCache,
                                   Set<String> skuSet,
                                   Set<String> barcodeSet) {
        if (ruleCache.containsKey(item.getProductName())) return true;
        String code = item.getProductCode();
        if (code == null || code.isBlank() || isChannelProductCode(code)) return false;
        String lower = code.toLowerCase();
        return skuSet.contains(lower) || barcodeSet.contains(lower);
    }

    /**
     * 재고 상품 검색
     * GET /api/matching/search?keyword=후디
     */
    @GetMapping("/search")
    public ResponseEntity<List<ProductCandidateDTO>> search(@RequestParam String keyword) {
        List<Product> products = productRepository.searchProducts(keyword);
        List<ProductCandidateDTO> result = products.stream()
            .limit(20)
            .map(ProductCandidateDTO::new)
            .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    /**
     * 매칭 확정
     * POST /api/matching/match
     * Body: { "itemId": "...", "productId": "...", "channelProductName": "..." }
     */
    @PostMapping("/match")
    @Transactional
    public ResponseEntity<Map<String, Object>> match(@RequestBody Map<String, String> body) {
        String itemId            = body.get("itemId");
        String productId         = body.get("productId");
        String channelProductName = body.get("channelProductName");

        // 재고 상품 조회
        Product product = productRepository.findById(UUID.fromString(productId))
            .orElseThrow(() -> new RuntimeException("상품을 찾을 수 없습니다"));

        // OrderItem productCode 업데이트
        updateOrderItemProductCode(itemId, product);

        // 매칭 룰 저장 (없으면 추가, 있으면 업데이트)
        ProductMatchingRule rule = ruleRepository
            .findByChannelProductName(channelProductName)
            .orElse(ProductMatchingRule.builder()
                .channelProductName(channelProductName)
                .build());

        rule.setProductId(product.getProductId());
        rule.setProductName(product.getProductName());
        rule.setSku(product.getSku());
        rule.setMatchType("MANUAL");
        ruleRepository.save(rule);

        log.info("매칭 확정: '{}' → {} ({})", channelProductName, product.getProductName(), product.getSku());
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "매칭 완료: " + product.getProductName()
        ));
    }

    /**
     * 자동매칭 실행
     * POST /api/matching/auto
     */
    @PostMapping("/auto")
    @Transactional
    public ResponseEntity<Map<String, Object>> autoMatch() {
        log.info("자동매칭 실행");

        List<Order> orders = new ArrayList<>();
        for (Order.OrderStatus st : new Order.OrderStatus[]{Order.OrderStatus.PENDING, Order.OrderStatus.CONFIRMED}) {
            int p = 0; while(true) {
                var pg = PageRequest.of(p++, 500, Sort.by(Sort.Direction.DESC, "orderedAt"));
                var sl = orderRepository.findByOrderStatus(st, pg);
                orders.addAll(sl.getContent()); if(!sl.hasNext()) break;
            }
        }
        orders.forEach(o -> o.getItems().size());

        // ── 캐시 사전 로딩 ──────────────────────────────────────
        Map<String, ProductMatchingRule> ruleCache = new HashMap<>();
        ruleRepository.findAllByOrderByCreatedAtDesc()
            .forEach(r -> ruleCache.put(r.getChannelProductName(), r));

        List<Product> allProducts = productRepository.findAll();
        Map<String, Product> skuMap     = new HashMap<>();
        Map<String, Product> barcodeMap = new HashMap<>();
        allProducts.forEach(p -> {
            if (p.getSku()     != null) skuMap.put(p.getSku().toLowerCase(), p);
            if (p.getBarcode() != null) barcodeMap.put(p.getBarcode().toLowerCase(), p);
        });
        // ────────────────────────────────────────────────────────

        int matched = 0;
        int skipped = 0;

        for (Order o : orders) {
            for (OrderItem item : o.getItems()) {
                Set<String> skuSet     = skuMap.keySet();
                Set<String> barcodeSet = barcodeMap.keySet();
                if (isMatchedFast(item, ruleCache, skuSet, barcodeSet)) continue;

                // [1] 바코드(productCode) 직접 매칭 — Map 룩업
                if (item.getProductCode() != null && !item.getProductCode().isBlank()
                        && !item.getProductCode().startsWith("FAKE")
                        && !item.getProductCode().startsWith("NOINSTOCK")) {
                    String lower = item.getProductCode().toLowerCase();
                    Product exactMatch = skuMap.containsKey(lower) ? skuMap.get(lower)
                                       : barcodeMap.get(lower);
                    if (exactMatch != null) {
                        if (!ruleCache.containsKey(item.getProductName())) {
                            ProductMatchingRule newRule = ProductMatchingRule.builder()
                                .channelProductName(item.getProductName())
                                .productId(exactMatch.getProductId())
                                .productName(exactMatch.getProductName())
                                .sku(exactMatch.getSku())
                                .matchType("AUTO")
                                .build();
                            ruleRepository.save(newRule);
                            ruleCache.put(item.getProductName(), newRule);
                        }
                        matched++;
                        continue;
                    }
                }

                // [2] 룰 캐시로 매칭
                ProductMatchingRule rule = ruleCache.get(item.getProductName());
                if (rule != null) {
                    Product product = skuMap.values().stream()
                        .filter(p -> p.getProductId().equals(rule.getProductId()))
                        .findFirst().orElse(null);
                    if (product != null) {
                        item.setProductCode(product.getSku());
                        orderRepository.save(o);
                        matched++;
                        continue;
                    }
                }

                // [3] 유사도 매칭 — 캐시된 목록 사용
                Product best = findBestMatchFromCache(item.getProductName(), allProducts);
                if (best != null) {
                    item.setProductCode(best.getSku());
                    orderRepository.save(o);
                    if (!ruleCache.containsKey(item.getProductName())) {
                        ProductMatchingRule newRule = ProductMatchingRule.builder()
                            .channelProductName(item.getProductName())
                            .productId(best.getProductId())
                            .productName(best.getProductName())
                            .sku(best.getSku())
                            .matchType("AUTO")
                            .build();
                        ruleRepository.save(newRule);
                        ruleCache.put(item.getProductName(), newRule);
                    }
                    matched++;
                    continue;
                }
                skipped++;
            }
        }

        log.info("자동매칭 완료: {}건 매칭, {}건 미매칭", matched, skipped);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "matched", matched,
            "skipped", skipped,
            "message", matched + "건 자동 매칭 완료, " + skipped + "건 수동 필요"
        ));
    }

    /**
     * 저장된 매칭 룰 목록
     * GET /api/matching/rules
     */
    @GetMapping("/rules")
    public ResponseEntity<List<ProductMatchingRule>> getRules() {
        return ResponseEntity.ok(ruleRepository.findAllByOrderByCreatedAtDesc());
    }

    /**
     * 룰 삭제
     * DELETE /api/matching/rules/{id}
     */
    @DeleteMapping("/rules/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteRule(@PathVariable UUID id) {
        ruleRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────

    private void updateOrderItemProductCode(String itemId, Product product) {
        // itemId로 직접 OrderItem 조회 후 업데이트 (전체 주문 스캔 방지)
        List<Order> orders = new ArrayList<>();
        for (Order.OrderStatus st : new Order.OrderStatus[]{Order.OrderStatus.PENDING, Order.OrderStatus.CONFIRMED}) {
            int p = 0; while(true) {
                var pg = PageRequest.of(p++, 500, Sort.by(Sort.Direction.DESC, "orderedAt"));
                var sl = orderRepository.findByOrderStatus(st, pg);
                orders.addAll(sl.getContent()); if(!sl.hasNext()) break;
            }
        }
        for (Order o : orders) {
            o.getItems().size();
            for (OrderItem item : o.getItems()) {
                if (item.getItemId() != null && item.getItemId().toString().equals(itemId)) {
                    item.setProductCode(product.getSku());
                    orderRepository.save(o);
                    log.info("OrderItem 업데이트: {} → {}", itemId, product.getSku());
                    return;
                }
            }
        }
        log.warn("OrderItem을 찾을 수 없음: {}", itemId);
    }

    /**
     * 상품명 정규화 (특수문자 제거, 소문자, 토큰화)
     * "XFL1TL1003 블랙 / M(55반~66)" → ["xfl1tl1003", "블랙", "m55반66"]
     */
    private Set<String> tokenize(String s) {
        if (s == null || s.isBlank()) return new HashSet<>();
        String normalized = s.replaceAll("[/\\\\|·•\\-_]", " ")
                             .replaceAll("\\s+", " ")
                             .trim()
                             .toLowerCase();
        return new HashSet<>(Arrays.asList(normalized.split(" ")));
    }

    /**
     * Jaccard 유사도 (0.0 ~ 1.0)
     * 0.4 이상이면 매칭 후보로 간주
     */
    private double similarity(String a, String b) {
        Set<String> ta = tokenize(a);
        Set<String> tb = tokenize(b);
        if (ta.isEmpty() || tb.isEmpty()) return 0.0;
        Set<String> inter = new HashSet<>(ta);
        inter.retainAll(tb);
        Set<String> union = new HashSet<>(ta);
        union.addAll(tb);
        return (double) inter.size() / union.size();
    }

    private static final double SIMILARITY_THRESHOLD = 0.4;

    /**
     * 상품명 유사도 기반 최적 상품 탐색
     * 검색 키워드로 후보군을 뽑은 뒤, 전체 상품명 유사도로 최적 선택
     */
    /**
     * 캐시된 상품 목록에서 유사도 기반 최적 매칭 (DB 조회 없음)
     */
    private Product findBestMatchFromCache(String channelProductName, List<Product> allProducts) {
        if (channelProductName == null || channelProductName.isBlank()) return null;
        if (allProducts.isEmpty()) return null;

        String code = channelProductName.split(" ")[0];
        if (code.length() > 12) code = code.substring(0, 12);
        final String codeFilter = code.toLowerCase();

        // 코드 앞부분으로 1차 필터링
        List<Product> candidates = allProducts.stream()
            .filter(p -> p.getProductName() != null &&
                (p.getProductName().toLowerCase().contains(codeFilter) ||
                 (p.getSku() != null && p.getSku().toLowerCase().contains(codeFilter))))
            .collect(Collectors.toList());

        if (candidates.isEmpty()) candidates = allProducts;

        return candidates.stream()
            .max(Comparator.comparingDouble(p -> similarity(channelProductName, p.getProductName())))
            .filter(p -> similarity(channelProductName, p.getProductName()) >= SIMILARITY_THRESHOLD)
            .orElse(null);
    }

    private Product findBestMatch(String channelProductName) {
        if (channelProductName == null || channelProductName.isBlank()) return null;

        // 코드 부분 추출 (첫 번째 공백 전 또는 앞 10자)
        String code = channelProductName.split(" ")[0];
        if (code.length() > 12) code = code.substring(0, 12);

        List<Product> candidates = productRepository.searchProducts(code);

        // 후보가 없으면 전체 상품명 앞부분으로 재검색
        if (candidates.isEmpty()) {
            String fallback = channelProductName.length() > 8
                ? channelProductName.substring(0, 8) : channelProductName;
            candidates = productRepository.searchProducts(fallback);
        }

        if (candidates.isEmpty()) return null;

        // 전체 상품명 유사도로 최적 선택
        return candidates.stream()
            .max(Comparator.comparingDouble(p -> similarity(channelProductName, p.getProductName())))
            .filter(p -> similarity(channelProductName, p.getProductName()) >= SIMILARITY_THRESHOLD)
            .orElse(null);
    }

    private String extractKeyword(String productName) {
        if (productName == null) return "";
        String cleaned = productName.replaceAll("[/\\\\|·•].*", "").trim();
        return cleaned.length() > 10 ? cleaned.substring(0, 10) : cleaned;
    }
}
