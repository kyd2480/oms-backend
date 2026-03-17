package com.oms.collector.controller;

import com.oms.collector.entity.Order;
import com.oms.collector.repository.OrderRepository;
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
 * 송장 컨트롤러
 *
 * GET  /api/invoice/orders          - 송장 입력 대상 주문 목록 (CONFIRMED)
 * POST /api/invoice/save            - 송장번호 저장
 * POST /api/invoice/save-bulk       - 일괄 저장
 * GET  /api/invoice/completed       - 송장 입력 완료 목록
 */
@Slf4j
@RestController
@RequestMapping("/api/invoice")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class InvoiceController {

    private final OrderRepository orderRepository;

    // 택배사 목록
    public static final List<Map<String, String>> CARRIERS = List.of(
        Map.of("code", "CJ",      "name", "CJ대한통운"),
        Map.of("code", "POST",    "name", "우체국택배"),
        Map.of("code", "HANJIN",  "name", "한진택배"),
        Map.of("code", "LOTTE",   "name", "롯데택배"),
        Map.of("code", "LOGEN",   "name", "로젠택배"),
        Map.of("code", "DIRECT",  "name", "직접배송")
    );

    // ─── DTO ─────────────────────────────────────────────────────
    public static class InvoiceOrderDTO {
        public String orderNo;
        public String channelName;
        public String recipientName;
        public String recipientPhone;
        public String address;
        public String productName;
        public int    quantity;
        public String orderedAt;
        public String carrierCode;   // 택배사 코드
        public String carrierName;   // 택배사명
        public String trackingNo;    // 송장번호
        public boolean hasInvoice;   // 송장 입력 여부

        public InvoiceOrderDTO(Order o) {
            this.orderNo       = o.getOrderNo();
            this.channelName   = o.getChannel() != null ? o.getChannel().getChannelName() : "";
            this.recipientName  = o.getRecipientName();
            this.recipientPhone = o.getRecipientPhone();
            this.address       = (o.getAddress() != null ? o.getAddress() : "")
                               + (o.getAddressDetail() != null ? " " + o.getAddressDetail() : "");
            this.productName   = o.getItems().isEmpty() ? "" :
                o.getItems().stream().map(i -> i.getProductName()).collect(Collectors.joining(", "));
            this.quantity      = o.getItems().stream()
                .mapToInt(i -> i.getQuantity() != null ? i.getQuantity() : 0).sum();
            this.orderedAt     = o.getOrderedAt() != null ? o.getOrderedAt().toString() : "";
            // 배송 메모에서 송장 정보 파싱 (저장 형식: "CARRIER:CJ|TRACKING:1234567890")
            parseInvoiceFromMemo(o.getDeliveryMemo());
        }

        private void parseInvoiceFromMemo(String memo) {
            if (memo == null || !memo.startsWith("INVOICE:")) {
                this.hasInvoice = false;
                return;
            }
            try {
                String[] parts = memo.substring(8).split("\\|");
                for (String part : parts) {
                    String[] kv = part.split(":", 2);
                    if (kv.length == 2) {
                        if ("CARRIER".equals(kv[0])) this.carrierCode = kv[1];
                        if ("CARRIER_NAME".equals(kv[0])) this.carrierName = kv[1];
                        if ("TRACKING".equals(kv[0])) this.trackingNo = kv[1];
                    }
                }
                this.hasInvoice = this.trackingNo != null && !this.trackingNo.isBlank();
            } catch (Exception e) {
                this.hasInvoice = false;
            }
        }
    }

    /**
     * 택배사 목록
     * GET /api/invoice/carriers
     */
    @GetMapping("/carriers")
    public ResponseEntity<List<Map<String, String>>> getCarriers() {
        return ResponseEntity.ok(CARRIERS);
    }

    /**
     * 송장 입력 대상 목록 (CONFIRMED - 재고할당 완료)
     * GET /api/invoice/orders
     */
    @GetMapping("/orders")
    @Transactional(readOnly = true)
    public ResponseEntity<List<InvoiceOrderDTO>> getOrders(
        @RequestParam(defaultValue = "0")   int page,
        @RequestParam(defaultValue = "200") int size
    ) {
        var pageable = PageRequest.of(page, Math.min(size, 200),
            Sort.by(Sort.Direction.DESC, "orderedAt"));

        List<Order> orders = orderRepository
            .findByOrderStatus(Order.OrderStatus.CONFIRMED, pageable).getContent();

        orders.forEach(o -> {
            o.getItems().size();
            if (o.getChannel() != null) o.getChannel().getChannelName();
        });

        List<InvoiceOrderDTO> result = orders.stream()
            .map(InvoiceOrderDTO::new)
            .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /**
     * 송장번호 저장 (deliveryMemo 활용)
     * POST /api/invoice/save
     * Body: { "orderNo": "OMS-...", "carrierCode": "CJ", "carrierName": "CJ대한통운", "trackingNo": "1234567890" }
     */
    @PostMapping("/save")
    @Transactional
    public ResponseEntity<Map<String, Object>> saveInvoice(
        @RequestBody Map<String, String> body
    ) {
        String orderNo     = body.get("orderNo");
        String carrierCode = body.get("carrierCode");
        String carrierName = body.get("carrierName");
        String trackingNo  = body.get("trackingNo");

        if (orderNo == null || trackingNo == null || trackingNo.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "필수값 누락"));
        }

        Order order = orderRepository.findByOrderNo(orderNo)
            .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다: " + orderNo));

        // deliveryMemo에 송장 정보 저장
        order.setDeliveryMemo(
            "INVOICE:CARRIER:" + carrierCode +
            "|CARRIER_NAME:" + carrierName +
            "|TRACKING:" + trackingNo
        );
        orderRepository.save(order);

        log.info("송장 저장: {} → {} {}", orderNo, carrierName, trackingNo);
        return ResponseEntity.ok(Map.of("success", true, "message", "송장 저장 완료"));
    }

    /**
     * 일괄 송장 저장
     * POST /api/invoice/save-bulk
     * Body: [{ "orderNo": "...", "carrierCode": "...", "carrierName": "...", "trackingNo": "..." }]
     */
    @PostMapping("/save-bulk")
    @Transactional
    public ResponseEntity<Map<String, Object>> saveBulk(
        @RequestBody List<Map<String, String>> list
    ) {
        int saved = 0, failed = 0;
        for (Map<String, String> body : list) {
            try {
                String orderNo    = body.get("orderNo");
                String carrierCode = body.get("carrierCode");
                String carrierName = body.get("carrierName");
                String trackingNo  = body.get("trackingNo");
                if (orderNo == null || trackingNo == null || trackingNo.isBlank()) { failed++; continue; }
                Order order = orderRepository.findByOrderNo(orderNo).orElse(null);
                if (order == null) { failed++; continue; }
                order.setDeliveryMemo(
                    "INVOICE:CARRIER:" + carrierCode +
                    "|CARRIER_NAME:" + carrierName +
                    "|TRACKING:" + trackingNo
                );
                orderRepository.save(order);
                saved++;
            } catch (Exception e) { failed++; }
        }
        return ResponseEntity.ok(Map.of("success", true, "saved", saved, "failed", failed,
            "message", saved + "건 저장 완료"));
    }

    /**
     * 송장 입력 완료 목록
     * GET /api/invoice/completed
     */
    @GetMapping("/completed")
    @Transactional(readOnly = true)
    public ResponseEntity<List<InvoiceOrderDTO>> getCompleted(
        @RequestParam(defaultValue = "0")   int page,
        @RequestParam(defaultValue = "200") int size
    ) {
        var pageable = PageRequest.of(page, Math.min(size, 200),
            Sort.by(Sort.Direction.DESC, "orderedAt"));

        List<Order> orders = orderRepository
            .findByOrderStatus(Order.OrderStatus.CONFIRMED, pageable).getContent();

        orders.forEach(o -> {
            o.getItems().size();
            if (o.getChannel() != null) o.getChannel().getChannelName();
        });

        List<InvoiceOrderDTO> result = orders.stream()
            .map(InvoiceOrderDTO::new)
            .filter(dto -> dto.hasInvoice)
            .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /**
     * 송장번호 자동 부여 (단건)
     * POST /api/invoice/auto-assign/{orderNo}
     */
    @PostMapping("/auto-assign/{orderNo}")
    @Transactional
    public ResponseEntity<Map<String, Object>> autoAssign(
        @PathVariable String orderNo,
        @RequestBody(required = false) Map<String, String> body
    ) {
        String carrierCode = body != null ? body.getOrDefault("carrierCode", "POST") : "POST";
        String carrierName = body != null ? body.getOrDefault("carrierName", "우체국택배") : "우체국택배";

        Order order = orderRepository.findByOrderNo(orderNo)
            .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다: " + orderNo));

        String trackingNo = generateTrackingNo(carrierCode);
        order.setDeliveryMemo(
            "INVOICE:CARRIER:" + carrierCode +
            "|CARRIER_NAME:" + carrierName +
            "|TRACKING:" + trackingNo
        );
        orderRepository.save(order);

        log.info("송장 자동부여: {} → {} {}", orderNo, carrierName, trackingNo);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "trackingNo", trackingNo,
            "carrierCode", carrierCode,
            "carrierName", carrierName,
            "message", "송장번호 자동 부여 완료: " + trackingNo
        ));
    }

    /**
     * 전체 일괄 자동부여
     * POST /api/invoice/auto-assign-all
     * Body: { "carrierCode": "POST", "carrierName": "우체국택배" }
     */
    @PostMapping("/auto-assign-all")
    @Transactional
    public ResponseEntity<Map<String, Object>> autoAssignAll(
        @RequestBody Map<String, String> body
    ) {
        String carrierCode = body.getOrDefault("carrierCode", "POST");
        String carrierName = body.getOrDefault("carrierName", "우체국택배");

        var pageable = PageRequest.of(0, 500, Sort.by(Sort.Direction.DESC, "orderedAt"));
        List<Order> orders = orderRepository.findByOrderStatus(Order.OrderStatus.CONFIRMED, pageable).getContent();
        orders.forEach(o -> o.getItems().size());

        int assigned = 0;
        for (Order order : orders) {
            // 이미 송장 있으면 스킵
            if (order.getDeliveryMemo() != null && order.getDeliveryMemo().startsWith("INVOICE:")) continue;
            String trackingNo = generateTrackingNo(carrierCode);
            order.setDeliveryMemo(
                "INVOICE:CARRIER:" + carrierCode +
                "|CARRIER_NAME:" + carrierName +
                "|TRACKING:" + trackingNo
            );
            orderRepository.save(order);
            assigned++;
        }

        log.info("송장 일괄 자동부여: {}건 ({})", assigned, carrierName);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "assigned", assigned,
            "message", assigned + "건 송장번호 자동 부여 완료"
        ));
    }

    // ─── 임시 송장번호 생성 (실제 API 연결 시 교체) ──────────────
    // ⚠️ 택배사 API 연결 후 이 메서드 제거하고 실제 API 호출로 교체
    private String generateTrackingNo(String carrierCode) {
        String prefix = switch (carrierCode) {
            case "CJ"     -> "6";
            case "POST"   -> "6";
            case "HANJIN" -> "7";
            case "LOTTE"  -> "8";
            case "LOGEN"  -> "9";
            default       -> "6";
        };
        // 13자리 임의 번호 생성
        long seq = System.currentTimeMillis() % 1_000_000_000_000L;
        return prefix + String.format("%012d", seq);
    }

}