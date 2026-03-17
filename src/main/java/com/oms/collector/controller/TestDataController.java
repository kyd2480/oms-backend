package com.oms.collector.controller;

import com.oms.collector.entity.Order;
import com.oms.collector.entity.OrderItem;
import com.oms.collector.entity.Product;
import com.oms.collector.entity.SalesChannel;
import com.oms.collector.repository.OrderRepository;
import com.oms.collector.repository.ProductRepository;
import com.oms.collector.repository.SalesChannelRepository;
import com.oms.collector.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * ⚠️ 테스트 전용 컨트롤러
 *
 * 재고 기반 더미 주문 데이터 삽입용
 * 실제 쇼핑몰 API 연결 후 이 파일 삭제
 *
 * POST /api/test/orders/insert  - 테스트 주문 삽입
 * DELETE /api/test/orders/clear - 테스트 주문 전체 삭제
 */
@Slf4j
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TestDataController {

    private final OrderRepository       orderRepository;
    private final SalesChannelRepository salesChannelRepository;
    private final ProductRepository     productRepository;
    private final InventoryService      inventoryService;

    /**
     * 테스트 주문 삽입
     * POST /api/test/orders/insert
     *
     * Body: [{
     *   productName, optionName, barcode, quantity,
     *   receiverName, receiverPhone, address, channel, salePrice
     * }]
     */
    @PostMapping("/orders/insert")
    @Transactional
    public ResponseEntity<Map<String, Object>> insertTestOrders(
        @RequestBody List<Map<String, Object>> orderList
    ) {
        log.info("⚠️ 테스트 주문 삽입: {}건", orderList.size());

        int saved   = 0;
        int skipped = 0;
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        for (Map<String, Object> raw : orderList) {
            try {
                // 자동 주문번호 생성
                String orderNo = "TEST-" + today + "-" + String.format("%04d", saved + 1);

                // 중복 체크
                if (orderRepository.findByOrderNo(orderNo).isPresent()) {
                    skipped++;
                    continue;
                }

                // 채널 조회
                String channelName = (String) raw.getOrDefault("channel", "스마트스토어");
                SalesChannel channel = findChannel(channelName);

                // 금액
                BigDecimal price = BigDecimal.ZERO;
                Object priceObj  = raw.get("salePrice");
                if (priceObj != null) {
                    try { price = new BigDecimal(priceObj.toString()); }
                    catch (Exception ignored) {}
                }

                int qty = 1;
                Object qtyObj = raw.get("quantity");
                if (qtyObj != null) {
                    try { qty = Integer.parseInt(qtyObj.toString()); }
                    catch (Exception ignored) {}
                }

                // 주문 생성
                Order order = Order.builder()
                    .orderNo(orderNo)
                    .channel(channel)
                    .channelOrderNo("CH-" + orderNo)
                    .customerName((String) raw.getOrDefault("receiverName", "테스트"))
                    .customerPhone((String) raw.getOrDefault("receiverPhone", "010-0000-0000"))
                    .recipientName((String) raw.getOrDefault("receiverName", "테스트"))
                    .recipientPhone((String) raw.getOrDefault("receiverPhone", "010-0000-0000"))
                    .address((String) raw.getOrDefault("address", "테스트 주소"))
                    .totalAmount(price.multiply(BigDecimal.valueOf(qty)))
                    .paymentAmount(price.multiply(BigDecimal.valueOf(qty)))
                    .orderStatus(Order.OrderStatus.PENDING)
                    .paymentStatus(Order.PaymentStatus.PAID)
                    .orderedAt(LocalDateTime.now().minusMinutes(new Random().nextInt(1440)))
                    .build();

                // 상품 아이템
                String productName = (String) raw.getOrDefault("productName", "");
                String optionName  = (String) raw.getOrDefault("optionName",  "");
                String barcode     = (String) raw.getOrDefault("barcode",     "");

                OrderItem item = OrderItem.builder()
                    .productCode(barcode)          // 바코드를 productCode로 사용
                    .channelProductCode(barcode)
                    .productName(productName + (optionName.isBlank() ? "" : " / " + optionName))
                    .optionName(optionName)
                    .quantity(qty)
                    .unitPrice(price)
                    .totalPrice(price.multiply(BigDecimal.valueOf(qty)))
                    .build();

                order.addItem(item);
                orderRepository.save(order);
                saved++;

            } catch (Exception e) {
                log.error("테스트 주문 삽입 실패: {}", e.getMessage());
                skipped++;
            }
        }

        log.info("테스트 주문 삽입 완료: {}건 저장, {}건 스킵", saved, skipped);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "saved",   saved,
            "skipped", skipped,
            "message", saved + "건 테스트 주문 삽입 완료"
        ));
    }

    /**
     * 테스트 주문 전체 삭제 (TEST- 로 시작하는 주문만)
     * DELETE /api/test/orders/clear
     */
    @DeleteMapping("/orders/clear")
    @Transactional
    public ResponseEntity<Map<String, Object>> clearTestOrders() {
        log.info("⚠️ 테스트 주문 삭제");

        List<Order> testOrders = orderRepository.findAll().stream()
            .filter(o -> o.getOrderNo() != null && o.getOrderNo().startsWith("TEST-"))
            .toList();

        orderRepository.deleteAll(testOrders);
        log.info("테스트 주문 {}건 삭제", testOrders.size());

        return ResponseEntity.ok(Map.of(
            "success", true,
            "deleted", testOrders.size(),
            "message", testOrders.size() + "건 테스트 주문 삭제 완료"
        ));
    }

    // ─── 채널 조회 헬퍼 ──────────────────────────────────────────
    private SalesChannel findChannel(String channelName) {
        return salesChannelRepository.findAll().stream()
            .filter(c -> c.getChannelName() != null &&
                         c.getChannelName().contains(channelName))
            .findFirst()
            .orElse(null);
    }
}

    /**
     * 주문 아이템 샘플 조회 (데이터 구조 확인용)
     * GET /api/test/orders/sample
     */
    @GetMapping("/orders/sample")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> sampleOrders() {
        Pageable p = org.springframework.data.domain.PageRequest.of(0, 10,
            org.springframework.data.domain.Sort.by("orderedAt").descending());
        var orders = orderRepository.findAll(p).getContent();
        List<Map<String, Object>> result = new ArrayList<>();
        for (var o : orders) {
            o.getItems().size();
            for (var item : o.getItems()) {
                result.add(Map.of(
                    "orderNo",            o.getOrderNo(),
                    "productName",        item.getProductName() != null ? item.getProductName() : "",
                    "productCode",        item.getProductCode() != null ? item.getProductCode() : "(없음)",
                    "channelProductCode", item.getChannelProductCode() != null ? item.getChannelProductCode() : "(없음)",
                    "optionName",         item.getOptionName() != null ? item.getOptionName() : ""
                ));
            }
        }
        return ResponseEntity.ok(result);
    
    /**
     * 쇼핑몰 상품코드로 잘못 저장된 product_code 초기화
     * PATCH /api/test/orders/fix-product-codes
     * 
     * 11ST-PRD-xxx, NAVER-PRD-xxx, CP-PRD-xxx 패턴을 null로 초기화
     * → 상품명 매칭 탭에서 정상 매칭 가능하게 됨
     */
    @PatchMapping("/orders/fix-product-codes")
    @Transactional
    public ResponseEntity<Map<String, Object>> fixProductCodes() {
        log.info("⚠️ 쇼핑몰 상품코드 → product_code 초기화");

        List<Order> orders = orderRepository.findAll();
        int fixed = 0;

        for (Order order : orders) {
            order.getItems().size();
            boolean changed = false;
            for (var item : order.getItems()) {
                String code = item.getProductCode();
                if (code != null && code.matches("(?i)(11ST|NAVER|CP|GS|COUPANG|KAKAO)-.*")) {
                    // channelProductCode로 이동 후 productCode 초기화
                    item.setChannelProductCode(code);
                    item.setProductCode(null);
                    changed = true;
                    fixed++;
                }
            }
            if (changed) orderRepository.save(order);
        }

        log.info("product_code 초기화 완료: {}건", fixed);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "fixed",   fixed,
            "message", fixed + "건 초기화 완료 (쇼핑몰 상품코드 → channelProductCode 이동)"
        ));
    }


    /**
     * CSV 재고 데이터 일괄 입고
     * POST /api/test/stock/inbound
     *
     * Body: [{
     *   barcode, productName, optionName,
     *   stockAnyang, stockIcheon, stockBucheon
     * }]
     * 
     * ⚠️ 실제 운영 시 이 API 삭제 후 정식 입고 프로세스 사용
     */
    @PostMapping("/stock/inbound")
    @Transactional
    public ResponseEntity<Map<String, Object>> inboundStock(
        @RequestBody List<Map<String, Object>> stockList
    ) {
        log.info("⚠️ CSV 재고 일괄 입고: {}개 항목", stockList.size());

        int updated = 0;
        int notFound = 0;

        for (Map<String, Object> item : stockList) {
            String barcode = (String) item.get("barcode");
            if (barcode == null || barcode.isBlank()) continue;

            int stockAnyang  = toInt(item.get("stockAnyang"));
            int stockIcheon  = toInt(item.get("stockIcheon"));
            int stockBucheon = toInt(item.get("stockBucheon"));

            // 바코드/SKU로 상품 검색
            List<Product> found = productRepository.searchProducts(barcode);
            Product product = found.stream()
                .filter(p -> barcode.equalsIgnoreCase(p.getSku())
                          || barcode.equalsIgnoreCase(p.getBarcode()))
                .findFirst().orElse(null);

            if (product == null) {
                notFound++;
                continue;
            }

            // 창고별 재고 직접 설정 (기존 재고 덮어쓰기)
            if (stockAnyang > 0) {
                product.setWarehouseStockAnyang(stockAnyang);
                product.increaseStock(stockAnyang);
            }
            if (stockIcheon > 0) {
                product.setWarehouseStockIcheon(stockIcheon);
                product.increaseStock(stockIcheon);
            }
            if (stockBucheon > 0) {
                product.setWarehouseStockBucheon(stockBucheon);
                product.increaseStock(stockBucheon);
            }

            productRepository.save(product);
            updated++;
        }

        log.info("재고 입고 완료: {}개 업데이트, {}개 미발견", updated, notFound);
        return ResponseEntity.ok(Map.of(
            "success",  true,
            "updated",  updated,
            "notFound", notFound,
            "message",  updated + "개 상품 재고 입고 완료 (" + notFound + "개 미발견)"
        ));
    }

    private int toInt(Object obj) {
        if (obj == null) return 0;
        try { return Integer.parseInt(obj.toString()); }
        catch (Exception e) { return 0; }
    }


}