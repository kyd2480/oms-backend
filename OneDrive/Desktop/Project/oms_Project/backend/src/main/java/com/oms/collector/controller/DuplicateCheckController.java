package com.oms.collector.controller;

import com.oms.collector.entity.Order;
import com.oms.collector.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 중복 주문 검사 컨트롤러
 *
 * 중복 기준:
 *   ORDER_NO         : 쇼핑몰 주문번호 완전 일치 (중복 수집된 경우)
 *   NAME_PHONE_ITEM  : 이름 + 연락처 + 상품명 일치 (같은 사람이 같은 상품 중복 주문)
 */
@Slf4j
@RestController
@RequestMapping("/api/processing/duplicate")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DuplicateCheckController {

    private final OrderRepository orderRepository;

    public enum Criteria {
        ORDER_NO,        // 쇼핑몰 주문번호 일치
        NAME_PHONE_ITEM  // 이름 + 연락처 + 상품명 일치
    }

    // ─── DTO ─────────────────────────────────────────────────────
    public static class DupOrderDTO {
        public String orderId;
        public String orderNo;
        public String channelOrderNo;
        public String channelName;
        public String recipientName;
        public String recipientPhone;
        public String address;
        public String totalAmount;
        public String orderStatus;
        public String orderedAt;

        public DupOrderDTO(Order o) {
            this.orderId        = o.getOrderId() != null ? o.getOrderId().toString() : "";
            this.orderNo        = o.getOrderNo();
            this.channelOrderNo = o.getChannelOrderNo();
            this.channelName    = o.getChannel() != null ? o.getChannel().getChannelName() : "";
            this.recipientName  = o.getRecipientName();
            this.recipientPhone = o.getRecipientPhone();
            this.address        = o.getAddress();
            this.totalAmount    = o.getTotalAmount() != null ? o.getTotalAmount().toPlainString() : "0";
            this.orderStatus    = o.getOrderStatus() != null ? o.getOrderStatus().name() : "";
            this.orderedAt      = o.getOrderedAt() != null ? o.getOrderedAt().toString() : "";
        }
    }

    public static class DupGroupDTO {
        public String groupKey;
        public int count;
        public List<DupOrderDTO> orders;

        public DupGroupDTO(String key, List<DupOrderDTO> orders) {
            this.groupKey = key;
            this.count    = orders.size();
            this.orders   = orders;
        }
    }

    public static class DupResultDTO {
        public int totalOrders;
        public int dupGroups;
        public int dupOrders;
        public List<DupGroupDTO> groups;

        public DupResultDTO(int total, List<DupGroupDTO> groups) {
            this.totalOrders = total;
            this.dupGroups   = groups.size();
            this.dupOrders   = groups.stream().mapToInt(g -> g.count).sum();
            this.groups      = groups;
        }
    }

    /**
     * 중복 검사
     * GET /api/processing/duplicate/check?criteria=ORDER_NO
     */
    @GetMapping("/check")
    @Transactional(readOnly = true)
    public ResponseEntity<DupResultDTO> checkDuplicates(
        @RequestParam(defaultValue = "ORDER_NO") Criteria criteria
    ) {
        log.info("중복 주문 검사: criteria={}", criteria);

        // 취소된 주문 제외
        List<Order> orders = orderRepository.findAll().stream()
            .filter(o -> o.getOrderStatus() != Order.OrderStatus.CANCELLED)
            .collect(Collectors.toList());

        // Lazy loading 초기화
        orders.forEach(o -> {
            o.getItems().size();
            if (o.getChannel() != null) o.getChannel().getChannelName();
        });

        // 그룹핑
        Map<String, List<Order>> grouped = new LinkedHashMap<>();
        for (Order o : orders) {
            String key = buildKey(o, criteria);
            if (key == null || key.isBlank() || key.startsWith("|") || key.endsWith("|")) continue;
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(o);
        }

        // 2건 이상인 그룹 = 중복, 최신순 정렬
        List<DupGroupDTO> dupGroups = grouped.entrySet().stream()
            .filter(e -> e.getValue().size() >= 2)
            .map(e -> {
                List<Order> sorted = e.getValue().stream()
                    .sorted(Comparator.comparing(
                        o -> o.getOrderedAt() != null ? o.getOrderedAt() : o.getCreatedAt(),
                        Comparator.nullsLast(Comparator.reverseOrder())
                    ))
                    .collect(Collectors.toList());
                return new DupGroupDTO(
                    e.getKey(),
                    sorted.stream().map(DupOrderDTO::new).collect(Collectors.toList())
                );
            })
            .sorted(Comparator.comparingInt((DupGroupDTO g) -> g.count).reversed())
            .collect(Collectors.toList());

        log.info("중복 검사 완료: 전체 {}건, {}그룹 중복", orders.size(), dupGroups.size());
        return ResponseEntity.ok(new DupResultDTO(orders.size(), dupGroups));
    }

    /**
     * 선택 주문 취소
     * POST /api/processing/duplicate/cancel
     * Body: { "orderNos": ["OMS-...", ...] }
     */
    @PostMapping("/cancel")
    @Transactional
    public ResponseEntity<Map<String, Object>> cancelOrders(
        @RequestBody Map<String, List<String>> body
    ) {
        List<String> orderNos = body.getOrDefault("orderNos", List.of());
        log.info("주문 취소 처리: {}건", orderNos.size());

        int cancelled = 0;
        for (String orderNo : orderNos) {
            Optional<Order> opt = orderRepository.findByOrderNo(orderNo);
            if (opt.isPresent()) {
                opt.get().setOrderStatus(Order.OrderStatus.CANCELLED);
                orderRepository.save(opt.get());
                cancelled++;
            }
        }
        return ResponseEntity.ok(Map.of("success", true, "cancelled", cancelled,
            "message", cancelled + "건 취소 완료"));
    }

    /**
     * 각 그룹 최신 1건만 남기고 나머지 자동 취소
     * POST /api/processing/duplicate/keep-latest
     * Body: { "criteria": "ORDER_NO" }
     */
    @PostMapping("/keep-latest")
    @Transactional
    public ResponseEntity<Map<String, Object>> keepLatest(
        @RequestBody Map<String, String> body
    ) {
        Criteria criteria;
        try {
            criteria = Criteria.valueOf(body.getOrDefault("criteria", "ORDER_NO"));
        } catch (IllegalArgumentException e) {
            criteria = Criteria.ORDER_NO;
        }
        log.info("최신 1건 유지 처리: criteria={}", criteria);

        List<Order> orders = orderRepository.findAll().stream()
            .filter(o -> o.getOrderStatus() != Order.OrderStatus.CANCELLED)
            .collect(Collectors.toList());

        orders.forEach(o -> {
            if (o.getChannel() != null) o.getChannel().getChannelName();
        });

        Map<String, List<Order>> grouped = new LinkedHashMap<>();
        for (Order o : orders) {
            String key = buildKey(o, criteria);
            if (key == null || key.isBlank()) continue;
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(o);
        }

        int cancelled = 0;
        for (List<Order> group : grouped.values()) {
            if (group.size() < 2) continue;
            // 최신순 정렬 후 첫 번째(최신)만 유지
            group.sort(Comparator.comparing(
                o -> o.getOrderedAt() != null ? o.getOrderedAt() : o.getCreatedAt(),
                Comparator.nullsLast(Comparator.reverseOrder())
            ));
            for (int i = 1; i < group.size(); i++) {
                group.get(i).setOrderStatus(Order.OrderStatus.CANCELLED);
                orderRepository.save(group.get(i));
                cancelled++;
            }
        }

        return ResponseEntity.ok(Map.of("success", true, "cancelled", cancelled,
            "message", cancelled + "건 취소 완료 (각 그룹 최신 1건 유지)"));
    }

    // ─── 그룹핑 키 생성 ──────────────────────────────────────────
    private String buildKey(Order o, Criteria criteria) {
        String phone   = norm(o.getRecipientPhone());
        String name    = norm(o.getRecipientName());
        String chNo    = norm(o.getChannelOrderNo());
        String orderNo = norm(o.getOrderNo());

        // 상품명은 RawOrder 또는 channelOrderNo 기반으로 대용
        // (OrderItem 엔티티 미확인 → channelOrderNo가 상품+주문 식별자 역할)
        return switch (criteria) {
            case ORDER_NO -> {
                // 쇼핑몰 주문번호 우선, 없으면 OMS 주문번호
                String key = !chNo.isBlank() ? chNo : orderNo;
                yield key.isBlank() ? "" : key;
            }
            case NAME_PHONE_ITEM -> {
                // 이름 + 연락처 + 첫 번째 상품명
                String productName = o.getItems().isEmpty() ? "" :
                    norm(o.getItems().get(0).getProductName());
                if (name.isBlank() || phone.isBlank() || productName.isBlank()) yield "";
                yield name + "|" + phone + "|" + productName;
            }
        };
    }

    private String norm(String s) {
        if (s == null) return "";
        return s.replaceAll("[^\\w가-힣]", "").toLowerCase();
    }
}
