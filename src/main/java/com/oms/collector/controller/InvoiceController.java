package com.oms.collector.controller;

import com.oms.collector.entity.Order;
import com.oms.collector.entity.OrderItem;
import com.oms.collector.entity.Product;
import com.oms.collector.repository.OrderRepository;
import com.oms.collector.repository.ProductRepository;
import com.oms.collector.service.InventoryService;
import com.oms.collector.service.postoffice.DeliveryAreaCodeService;
import com.oms.collector.service.tracking.TrackingNumberProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 송장 컨트롤러
 *
 * GET  /api/invoice/orders          - 송장 입력 대상 주문 목록 (CONFIRMED)
 * POST /api/invoice/save            - 송장번호 저장
 * POST /api/invoice/save-bulk       - 일괄 저장
 * GET  /api/invoice/completed       - 송장 입력 완료 목록
 * GET  /api/invoice/shipped         - 발송 완료 목록 (SHIPPED)
 * POST /api/invoice/auto-assign/{orderNo} - 단건 자동 부여
 * POST /api/invoice/auto-assign-all - 일괄 자동 부여
 * POST /api/invoice/cancel/{orderNo} - 발송취소 (SHIPPED → CONFIRMED)
 * POST /api/invoice/delete/{orderNo} - 송장삭제 (deliveryMemo 초기화)
 */
@Slf4j
@RestController
@RequestMapping("/api/invoice")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class InvoiceController {
    private static final String INVOICE_PREFIX = "INVOICE:";
    private static final String MESSAGE_PREFIX = "MESSAGE_B64:";

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final DeliveryAreaCodeService deliveryAreaCodeService;
    private final TrackingNumberProvider trackingNumberProvider;

    @Value("${tracking.post-office.order-company-name:}")
    private String senderCompanyName;

    @Value("${tracking.post-office.inquiry-tel:}")
    private String senderContact;

    @Value("${tracking.post-office.sender-zip:}")
    private String senderZip;

    @Value("${tracking.post-office.sender-address:}")
    private String senderAddress;

    @Value("${tracking.post-office.sender-address-detail:}")
    private String senderAddressDetail;

    @Value("${tracking.post-office.office-ser:}")
    private String officeSer;

    @Value("${tracking.post-office.content-code:}")
    private String contentCode;

    @Value("${tracking.post-office.contract-approval-no:}")
    private String contractApprovalNo;

    private record InvoiceInfo(String carrierCode, String carrierName, String trackingNo) {}

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

    /** 주문 내 개별 상품 아이템 */
    public static class OrderItemDTO {
        public String productName;  // 상품명
        public String option;       // 옵션 (색상/사이즈 등)
        public String barcode;      // 바코드 (추후 OrderItem 엔티티에 필드 추가 시 활성화)
        public String location;     // 재고 위치
        public int    quantity;     // 수량
        public int    originalQuantity;
        public int    cancelledQuantity;
        public String itemStatus;

        public OrderItemDTO(com.oms.collector.entity.OrderItem i, String location) {
            this.productName = i.getProductName();
            this.option      = i.getOptionName() != null ? i.getOptionName() : "";
            this.barcode     = i.getProductCode() != null ? i.getProductCode() : "";  // product_code = 바코드
            this.location    = location != null ? location : "";
            this.quantity    = i.getActiveQuantity();
            this.originalQuantity = i.getQuantity() != null ? i.getQuantity() : 1;
            this.cancelledQuantity = i.getCancelledQuantity() != null ? i.getCancelledQuantity() : 0;
            this.itemStatus = i.getItemStatus() != null ? i.getItemStatus().name() : "ACTIVE";
        }
    }

    public static class InvoiceOrderDTO {
        public String orderNo;
        public String channelName;
        public String recipientName;
        public String recipientPhone;
        public String postalCode;
        public String address;
        public String senderCompanyName;
        public String senderContact;
        public String senderZip;
        public String senderAddress;
        public String senderRoutePrimary;
        public String senderRouteSecondary;
        public String deliveryAreaCode;
        public String arrivalCenterName;
        public String deliveryPostOfficeName;
        public String deliveryCourseNo;
        public String productName;           // 상품명 합본 (하위호환 유지)
        public int    quantity;              // 총 수량 합계 (하위호환 유지)
        public String orderedAt;
        public String shippedAt;             // 출고(발송처리) 일시
        public String carrierCode;           // 택배사 코드
        public String carrierName;           // 택배사명
        public String trackingNo;            // 송장번호
        public String deliveryMessage;       // 배송메시지
        public boolean hasInvoice;           // 송장 입력 여부
        public List<OrderItemDTO> items;     // ★ 개별 상품 목록 (옵션·바코드 포함)

        public InvoiceOrderDTO(Order o, Map<String, Product> productMap, String senderCompanyName,
                               String senderContact, String senderZip, String senderAddress,
                               String senderRoutePrimary, String senderRouteSecondary,
                               String deliveryAreaCode, String arrivalCenterName,
                               String deliveryPostOfficeName, String deliveryCourseNo) {
            this.orderNo       = o.getOrderNo();
            this.channelName   = o.getChannel() != null ? o.getChannel().getChannelName() : "";
            this.recipientName  = o.getRecipientName();
            this.recipientPhone = o.getRecipientPhone();
            this.postalCode     = o.getPostalCode() != null ? o.getPostalCode() : "";
            this.address       = (o.getAddress() != null ? o.getAddress() : "")
                               + (o.getAddressDetail() != null ? " " + o.getAddressDetail() : "");
            this.senderCompanyName = senderCompanyName != null ? senderCompanyName : "";
            this.senderContact = senderContact != null ? senderContact : "";
            this.senderZip = senderZip != null ? senderZip : "";
            this.senderAddress = senderAddress != null ? senderAddress : "";
            this.senderRoutePrimary = senderRoutePrimary != null ? senderRoutePrimary : "";
            this.senderRouteSecondary = senderRouteSecondary != null ? senderRouteSecondary : "";
            this.deliveryAreaCode = deliveryAreaCode != null ? deliveryAreaCode : "";
            this.arrivalCenterName = arrivalCenterName != null ? arrivalCenterName : "";
            this.deliveryPostOfficeName = deliveryPostOfficeName != null ? deliveryPostOfficeName : "";
            this.deliveryCourseNo = deliveryCourseNo != null ? deliveryCourseNo : "";
            this.productName   = o.getItems().isEmpty() ? "" :
                o.getItems().stream()
                    .filter(i -> i.getActiveQuantity() > 0)
                    .map(i -> formatProductLabel(i.getProductName(), i.getOptionName()))
                    .collect(Collectors.joining(", "));
            this.quantity      = o.getItems().stream().mapToInt(OrderItem::getActiveQuantity).sum();
            this.orderedAt     = o.getOrderedAt() != null ? o.getOrderedAt().toString() : "";
            this.shippedAt     = o.getUpdatedAt() != null ? o.getUpdatedAt().toString() : "";
            // 개별 상품 목록 (옵션·바코드 포함)
            this.items         = o.getItems().stream()
                .filter(i -> i.getActiveQuantity() > 0)
                .map(i -> new OrderItemDTO(i, resolveLocation(productMap, i)))
                .collect(Collectors.toList());
            // 배송 메모에서 송장 정보 파싱 (저장 형식: "CARRIER:CJ|TRACKING:1234567890")
            parseInvoiceFromMemo(o.getDeliveryMemo());
        }

        private String resolveLocation(Map<String, Product> productMap, OrderItem item) {
            String productCode = item.getProductCode();
            if (productCode == null) {
                return "";
            }
            Product product = productMap.get(productCode.toLowerCase());
            if (product == null || product.getWarehouseLocation() == null) {
                return "";
            }
            return product.getWarehouseLocation().trim();
        }

        private String formatProductLabel(String productName, String optionName) {
            if (optionName == null || optionName.isBlank()) {
                return productName;
            }
            return productName + " / " + optionName;
        }

        private void parseInvoiceFromMemo(String memo) {
            this.deliveryMessage = extractDeliveryMessage(memo);
            if (memo == null || !memo.contains(INVOICE_PREFIX)) {
                this.hasInvoice = false;
                return;
            }
            try {
                String invoiceSegment = extractInvoiceSegment(memo);
                if (invoiceSegment == null || invoiceSegment.isBlank()) {
                    this.hasInvoice = false;
                    return;
                }
                String[] parts = invoiceSegment.split("\\|");
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

    private static String extractInvoiceSegment(String memo) {
        if (memo == null || memo.isBlank()) {
            return null;
        }
        int invoiceIndex = memo.indexOf(INVOICE_PREFIX);
        if (invoiceIndex < 0) {
            return null;
        }
        return memo.substring(invoiceIndex + INVOICE_PREFIX.length());
    }

    private static String extractDeliveryMessage(String memo) {
        if (memo == null || memo.isBlank()) {
            return "";
        }
        int messageIndex = memo.indexOf(MESSAGE_PREFIX);
        if (messageIndex >= 0) {
            int endIndex = memo.indexOf('|', messageIndex);
            String encoded = endIndex >= 0
                ? memo.substring(messageIndex + MESSAGE_PREFIX.length(), endIndex)
                : memo.substring(messageIndex + MESSAGE_PREFIX.length());
            try {
                return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                log.warn("배송메시지 디코딩 실패: {}", encoded);
                return "";
            }
        }
        if (memo.contains(INVOICE_PREFIX)) {
            return "";
        }
        return memo;
    }

    private static String buildDeliveryMemo(String existingMemo, String carrierCode, String carrierName, String trackingNo) {
        String deliveryMessage = extractDeliveryMessage(existingMemo);
        List<String> parts = new ArrayList<>();
        if (!deliveryMessage.isBlank()) {
            parts.add(MESSAGE_PREFIX + Base64.getUrlEncoder().encodeToString(deliveryMessage.getBytes(StandardCharsets.UTF_8)));
        }
        parts.add(
            INVOICE_PREFIX +
            "CARRIER:" + Objects.toString(carrierCode, "") +
            "|CARRIER_NAME:" + Objects.toString(carrierName, "") +
            "|TRACKING:" + Objects.toString(trackingNo, "")
        );
        return String.join("|", parts);
    }

    private static String removeInvoiceFromMemo(String memo) {
        String deliveryMessage = extractDeliveryMessage(memo);
        return deliveryMessage.isBlank() ? null : deliveryMessage;
    }

    private static InvoiceInfo extractInvoiceInfo(String memo) {
        String invoiceSegment = extractInvoiceSegment(memo);
        if (invoiceSegment == null || invoiceSegment.isBlank()) {
            return null;
        }

        String carrierCode = null;
        String carrierName = null;
        String trackingNo = null;
        for (String part : invoiceSegment.split("\\|")) {
            String[] kv = part.split(":", 2);
            if (kv.length != 2) {
                continue;
            }
            if ("CARRIER".equals(kv[0])) carrierCode = kv[1];
            if ("CARRIER_NAME".equals(kv[0])) carrierName = kv[1];
            if ("TRACKING".equals(kv[0])) trackingNo = kv[1];
        }
        return new InvoiceInfo(carrierCode, carrierName, trackingNo);
    }

    private void cancelCarrierInvoiceIfNeeded(Order order) {
        InvoiceInfo invoiceInfo = extractInvoiceInfo(order.getDeliveryMemo());
        if (invoiceInfo == null || invoiceInfo.trackingNo() == null || invoiceInfo.trackingNo().isBlank()) {
            return;
        }
        if (invoiceInfo.carrierCode() == null || invoiceInfo.carrierCode().isBlank()) {
            return;
        }
        trackingNumberProvider.cancel(
            invoiceInfo.carrierCode(),
            invoiceInfo.carrierName(),
            order.getOrderNo(),
            invoiceInfo.trackingNo()
        );
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
     * GET /api/invoice/orders?startDate=2026-01-01&endDate=2026-03-19
     * 날짜 미입력 시 전체 조회
     */
    @GetMapping("/orders")
    @Transactional(readOnly = true)
    public ResponseEntity<List<InvoiceOrderDTO>> getOrders(
        @RequestParam(defaultValue = "0")   int page,
        @RequestParam(defaultValue = "200") int size,
        @RequestParam(required = false) String startDate,
        @RequestParam(required = false) String endDate
    ) {
        List<Order> orders;
        if (startDate != null && endDate != null) {
            LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
            LocalDateTime end   = LocalDate.parse(endDate).atTime(23, 59, 59);
            orders = orderRepository.findByOrderStatusAndDateRange(Order.OrderStatus.CONFIRMED, start, end);
        } else {
            orders = new ArrayList<>();
            int p = 0; while(true) {
                var pg = PageRequest.of(p++, 500, Sort.by(Sort.Direction.DESC, "orderedAt"));
                var sl = orderRepository.findByOrderStatus(Order.OrderStatus.CONFIRMED, pg);
                orders.addAll(sl.getContent()); if(!sl.hasNext()) break;
            }
        }

        orders.forEach(o -> {
            o.getItems().size();
            if (o.getChannel() != null) o.getChannel().getChannelName();
        });

        Map<String, Product> productMap = getInvoiceProductMap();
        String fullSenderAddress = buildSenderAddress();
        List<InvoiceOrderDTO> result = orders.stream()
            .map(order -> toInvoiceOrderDTO(order, productMap, fullSenderAddress))
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
        order.setDeliveryMemo(buildDeliveryMemo(order.getDeliveryMemo(), carrierCode, carrierName, trackingNo));
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
                order.setDeliveryMemo(buildDeliveryMemo(order.getDeliveryMemo(), carrierCode, carrierName, trackingNo));
                orderRepository.save(order);
                saved++;
            } catch (Exception e) { failed++; }
        }
        return ResponseEntity.ok(Map.of("success", true, "saved", saved, "failed", failed,
            "message", saved + "건 저장 완료"));
    }

    /**
     * 송장 입력 완료 목록
     * GET /api/invoice/completed?startDate=2026-01-01&endDate=2026-03-19
     */
    @GetMapping("/completed")
    @Transactional(readOnly = true)
    public ResponseEntity<List<InvoiceOrderDTO>> getCompleted(
        @RequestParam(defaultValue = "0")   int page,
        @RequestParam(defaultValue = "200") int size,
        @RequestParam(required = false) String startDate,
        @RequestParam(required = false) String endDate
    ) {
        List<Order> orders;
        if (startDate != null && endDate != null) {
            LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
            LocalDateTime end   = LocalDate.parse(endDate).atTime(23, 59, 59);
            orders = orderRepository.findByOrderStatusAndDateRange(Order.OrderStatus.CONFIRMED, start, end);
        } else {
            orders = new ArrayList<>();
            int p = 0; while(true) {
                var pg = PageRequest.of(p++, 500, Sort.by(Sort.Direction.DESC, "orderedAt"));
                var sl = orderRepository.findByOrderStatus(Order.OrderStatus.CONFIRMED, pg);
                orders.addAll(sl.getContent()); if(!sl.hasNext()) break;
            }
        }

        orders.forEach(o -> {
            o.getItems().size();
            if (o.getChannel() != null) o.getChannel().getChannelName();
        });

        Map<String, Product> productMap = getInvoiceProductMap();
        String fullSenderAddress = buildSenderAddress();
        List<InvoiceOrderDTO> result = orders.stream()
            .map(order -> toInvoiceOrderDTO(order, productMap, fullSenderAddress))
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

        try {
            Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다: " + orderNo));

            String trackingNo = trackingNumberProvider.issue(carrierCode, carrierName, orderNo);
            order.setDeliveryMemo(buildDeliveryMemo(order.getDeliveryMemo(), carrierCode, carrierName, trackingNo));
            orderRepository.save(order);

            log.info("송장 자동부여: {} → {} {}", orderNo, carrierName, trackingNo);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "trackingNo", trackingNo,
                "carrierCode", carrierCode,
                "carrierName", carrierName,
                "message", "송장번호 자동 부여 완료: " + trackingNo
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("송장 자동부여 실패: {} - {}", orderNo, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("송장 자동부여 중 예기치 않은 오류: {}", orderNo, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "송장 자동 부여 처리 중 서버 오류가 발생했습니다."
            ));
        }
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

        List<Order> orders = new ArrayList<>();
        { int p = 0; while(true) {
            var pg = PageRequest.of(p++, 500, Sort.by(Sort.Direction.DESC, "orderedAt"));
            var sl = orderRepository.findByOrderStatus(Order.OrderStatus.CONFIRMED, pg);
            orders.addAll(sl.getContent()); if(!sl.hasNext()) break; } }
        orders.forEach(o -> o.getItems().size());

        int assigned = 0;
        List<Map<String, String>> failedOrders = new ArrayList<>();
        for (Order order : orders) {
            if (extractInvoiceSegment(order.getDeliveryMemo()) != null) continue;
            try {
                String trackingNo = trackingNumberProvider.issue(carrierCode, carrierName, order.getOrderNo());
                order.setDeliveryMemo(buildDeliveryMemo(order.getDeliveryMemo(), carrierCode, carrierName, trackingNo));
                orderRepository.save(order);
                assigned++;
            } catch (IllegalArgumentException | IllegalStateException e) {
                log.error("송장 일괄 자동부여 실패: {} - {}", order.getOrderNo(), e.getMessage(), e);
                failedOrders.add(Map.of(
                    "orderNo", order.getOrderNo(),
                    "message", e.getMessage()
                ));
            }
        }

        log.info("송장 일괄 자동부여: {}건 ({})", assigned, carrierName);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "assigned", assigned,
            "failed", failedOrders.size(),
            "failedOrders", failedOrders,
            "message", assigned + "건 송장번호 자동 부여 완료"
        ));
    }

    /**
     * 발송 완료 목록 (SHIPPED)
     * GET /api/invoice/shipped?startDate=2026-01-01&endDate=2026-03-19
     * 날짜는 발송처리 시각(updatedAt) 기준
     */
    @GetMapping("/shipped")
    @Transactional(readOnly = true)
    public ResponseEntity<List<InvoiceOrderDTO>> getShipped(
        @RequestParam(required = false) String startDate,
        @RequestParam(required = false) String endDate
    ) {
        List<Order> orders;
        if (startDate != null && endDate != null) {
            LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
            LocalDateTime end   = LocalDate.parse(endDate).atTime(23, 59, 59);
            orders = orderRepository.findByOrderStatusAndUpdatedAtRange(Order.OrderStatus.SHIPPED, start, end);
        } else {
            orders = new ArrayList<>();
            int p = 0; while(true) {
                var pg = PageRequest.of(p++, 500, Sort.by(Sort.Direction.DESC, "orderedAt"));
                var sl = orderRepository.findByOrderStatus(Order.OrderStatus.SHIPPED, pg);
                orders.addAll(sl.getContent()); if(!sl.hasNext()) break;
            }
        }

        orders.forEach(o -> {
            o.getItems().size();
            if (o.getChannel() != null) o.getChannel().getChannelName();
        });

        Map<String, Product> productMap = getInvoiceProductMap();
        String fullSenderAddress = buildSenderAddress();
        List<InvoiceOrderDTO> result = orders.stream()
            .map(order -> toInvoiceOrderDTO(order, productMap, fullSenderAddress))
            .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /**
     * 발송취소 (SHIPPED → CONFIRMED 롤백 + 재고 복구)
     * POST /api/invoice/cancel/{orderNo}
     */
    @PostMapping("/cancel/{orderNo}")
    @Transactional
    public ResponseEntity<Map<String, Object>> cancelShip(
        @PathVariable String orderNo
    ) {
        Order order = orderRepository.findByOrderNo(orderNo)
            .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다: " + orderNo));

        if (order.getOrderStatus() != Order.OrderStatus.SHIPPED) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "message", "SHIPPED 상태가 아닙니다: " + order.getOrderStatus()));
        }

        order.getItems().size();
        cancelCarrierInvoiceIfNeeded(order);

        // 발송 시 사용된 창고 코드
        String warehouseCode = AllocationController.getCurrentWarehouseCode();
        if (warehouseCode == null || warehouseCode.isBlank()) {
            log.warn("발송취소: 창고 코드 없음 — 재고 복구 없이 상태만 롤백 ({})", orderNo);
        }

        // 상품 캐시 로딩
        List<Product> allProducts = productRepository.findAll();
        Map<String, Product> skuMap     = new HashMap<>();
        Map<String, Product> barcodeMap = new HashMap<>();
        allProducts.forEach(p -> {
            if (p.getSku()     != null) skuMap.put(p.getSku().toLowerCase(), p);
            if (p.getBarcode() != null) barcodeMap.put(p.getBarcode().toLowerCase(), p);
        });

        int restored = 0;
        for (OrderItem item : order.getItems()) {
            String code = item.getProductCode();
            if (code == null || code.isBlank()) continue;

            Product product = skuMap.containsKey(code.toLowerCase())
                ? skuMap.get(code.toLowerCase())
                : barcodeMap.get(code.toLowerCase());
            if (product == null) continue;

            int qty = item.getQuantity() != null ? item.getQuantity() : 0;
            if (qty <= 0) continue;

            try {
                if (warehouseCode != null && !warehouseCode.isBlank()) {
                    // 1단계: totalStock + warehouseStock 복구
                    inventoryService.processInboundWithWarehouse(
                        product.getProductId(), qty, warehouseCode,
                        null, "발송취소 재고복구: " + orderNo
                    );
                } else {
                    inventoryService.processInbound(
                        product.getProductId(), qty, null,
                        "발송취소 재고복구 (창고미상): " + orderNo
                    );
                }
                // 2단계: 예약 상태 복원
                // 주문이 CONFIRMED로 돌아가므로 재고도 예약 상태로 전환
                // processInboundWithWarehouse → availableStock+qty
                // reserveStock → availableStock-qty, reservedStock+qty
                inventoryService.reserveStock(product.getProductId(), qty);

                restored++;
                log.info("재고 복구 완료: {} × {}개 / 창고:{} ({})", product.getSku(), qty, warehouseCode, orderNo);
            } catch (Exception e) {
                log.error("재고 복구 실패: {} - {}", orderNo, e.getMessage());
            }
        }

        order.setOrderStatus(Order.OrderStatus.CONFIRMED);
        orderRepository.save(order);
        log.info("발송취소 완료: {} → CONFIRMED (재고복구 {}건)", orderNo, restored);

        return ResponseEntity.ok(Map.of(
            "success",  true,
            "message",  "발송취소 완료 (재고 " + restored + "건 복구)",
            "restored", restored
        ));
    }

    /**
     * 송장삭제 (deliveryMemo 초기화)
     * POST /api/invoice/delete/{orderNo}
     */
    @PostMapping("/delete/{orderNo}")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteInvoice(
        @PathVariable String orderNo
    ) {
        Order order = orderRepository.findByOrderNo(orderNo)
            .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다: " + orderNo));

        try {
            cancelCarrierInvoiceIfNeeded(order);
            order.setDeliveryMemo(removeInvoiceFromMemo(order.getDeliveryMemo()));
            orderRepository.save(order);
            log.info("송장삭제: {}", orderNo);
            return ResponseEntity.ok(Map.of("success", true, "message", "송장 삭제 완료"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("송장삭제 실패 - 우체국 취소가 실패하여 로컬 송장을 유지합니다. orderNo={}, reason={}",
                orderNo, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "success", false,
                "message", "우체국 취소 실패: 송장이 유지되었습니다. " + e.getMessage()
            ));
        }
    }

    private Map<String, Product> getInvoiceProductMap() {
        Map<String, Product> productMap = new HashMap<>();
        for (Product product : productRepository.findAll()) {
            if (product.getSku() != null && !product.getSku().isBlank()) {
                productMap.put(product.getSku().toLowerCase(), product);
            }
            if (product.getBarcode() != null && !product.getBarcode().isBlank()) {
                productMap.put(product.getBarcode().toLowerCase(), product);
            }
        }
        return productMap;
    }

    private String buildSenderAddress() {
        String address = Objects.toString(senderAddress, "").trim();
        String detail = Objects.toString(senderAddressDetail, "").trim();
        if (address.isBlank()) {
            return "";
        }
        if (detail.isBlank()) {
            return address;
        }
        return address + " " + detail;
    }

    private String buildSenderRoutePrimary() {
        List<String> parts = new ArrayList<>();
        if (officeSer != null && !officeSer.isBlank()) parts.add("공급지 " + officeSer.trim());
        if (contentCode != null && !contentCode.isBlank()) parts.add("내용 " + contentCode.trim());
        return String.join("  ", parts);
    }

    private String buildSenderRouteSecondary() {
        if (contractApprovalNo == null || contractApprovalNo.isBlank()) {
            return "";
        }
        return "승인 " + contractApprovalNo.trim();
    }

    @GetMapping("/delivery-area-preview")
    public ResponseEntity<Map<String, Object>> previewDeliveryArea(
        @RequestParam String zip,
        @RequestParam String addr
    ) {
        DeliveryAreaCodeService.DeliveryAreaInfo info = deliveryAreaCodeService.lookup(zip, addr);
        return ResponseEntity.ok(Map.of(
            "configured", deliveryAreaCodeService.isConfigured(),
            "zip", zip,
            "addr", addr,
            "lastErrorMessage", Objects.toString(deliveryAreaCodeService.getLastErrorMessage(), ""),
            "deliveryAreaCode", info.deliveryAreaCode(),
            "arrivalCenterName", info.arrivalCenterName(),
            "deliveryPostOfficeName", info.deliveryPostOfficeName(),
            "deliveryCourseNo", info.courseNo(),
            "primaryLine", info.toPrimaryLine(),
            "secondaryLine", info.toSecondaryLine()
        ));
    }

    private InvoiceOrderDTO toInvoiceOrderDTO(Order order, Map<String, Product> productMap, String fullSenderAddress) {
        DeliveryAreaCodeService.DeliveryAreaInfo deliveryAreaInfo =
            deliveryAreaCodeService.lookup(order.getPostalCode(), buildRecipientAddress(order));

        boolean deliveryAreaConfigured = deliveryAreaCodeService.isConfigured();
        String senderRoutePrimary = deliveryAreaConfigured
            ? deliveryAreaInfo.toPrimaryLine()
            : buildSenderRoutePrimary();
        String senderRouteSecondary = deliveryAreaConfigured
            ? deliveryAreaInfo.toSecondaryLine()
            : buildSenderRouteSecondary();

        return new InvoiceOrderDTO(
            order,
            productMap,
            senderCompanyName,
            senderContact,
            senderZip,
            fullSenderAddress,
            senderRoutePrimary,
            senderRouteSecondary,
            deliveryAreaInfo.deliveryAreaCode(),
            deliveryAreaInfo.arrivalCenterName(),
            deliveryAreaInfo.deliveryPostOfficeName(),
            deliveryAreaInfo.courseNo()
        );
    }

    private String buildRecipientAddress(Order order) {
        String address = Objects.toString(order.getAddress(), "").trim();
        String detail = Objects.toString(order.getAddressDetail(), "").trim();
        if (address.isBlank()) {
            return "";
        }
        if (detail.isBlank()) {
            return address;
        }
        return address + " " + detail;
    }

}
