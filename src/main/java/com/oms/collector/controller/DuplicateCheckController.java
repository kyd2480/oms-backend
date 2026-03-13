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
 * GET  /api/processing/duplicate/check?criteria=PHONE_SKU
 * POST /api/processing/duplicate/cancel   (선택 주문 취소)
 * POST /api/processing/duplicate/keep-latest (각 그룹 최신 1건만 남기기)
 */
@Slf4j
@RestController
@RequestMapping("/api/processing/duplicate")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DuplicateCheckController {

    private final OrderRepository orderRepository;

    // ─── 중복 감지 기준 ────────────────────────────────────────────
    public enum Criteria {
        PHONE_SKU,          // 연락처 + 상품코드
        PHONE_PRODUCT,      // 연락처 + 상품명(앞 10자)
        NAME_ADDRESS_SKU,   // 수취인 + 주소(앞 15자) + 상품코드
        ORDER_NO            // 주문번호 완전일치
    }

    // ─── 응답 DTO ─────────────────────────────────────────────────
    public record DupOrderDTO(
        String orderId,
        String orderNo,
        String channelName,
        String recipientName,
        String recipientPhone,
        String address,
        String productName,
        String productCode,
        int    quantity,
        String orderStatus,
        String orderedAt
    ) {}

    public record DupGroupDTO(
        String groupKey,
        int    count,
        List<DupOrderDTO> orders
    ) {}

    public record DupResultDTO(
        int              totalOrders,
        int              dupGroups,
        int              dupOrders,
        List<DupGroupDTO> groups
    ) {}

    /**
     * 중복 주문 검사
     * GET /api/processing/duplicate/check?criteria=PHONE_SKU
     */
    @GetMapping("/check")
    @Transactional(readOnly = true)
    public ResponseEntity<DupResultDTO> checkDuplicates(
        @RequestParam(defaultValue = "PHONE_SKU") Criteria criteria
    ) {
        log.info("중복 주문 검사 시작: criteria={}", criteria);

        // 취소된 주문 제외하고 전체 조회
        List<Order> orders = orderRepository.findAll().stream()
            .filter(o -> !"CANCELLED".equals(o.getOrderStatus()))
            .collect(Collectors.toList());

        // Lazy loading 초기화
        orders.forEach(o -> {
            o.getItems().size();
            if (o.getChannel() != null) o.getChannel().getChannelName();
        });

        // 그룹핑 키 생성
        Map<String, List<Order>> grouped = new LinkedHashMap<>();
        for (Order o : orders) {
            String key = buildKey(o, criteria);
            if (key == null || key.isBlank() || key.equals("|") || key.equals("||")) continue;
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(o);
        }

        // 2건 이상인 그룹만 중복
        List<DupGroupDTO> dupGroups = grouped.entrySet().stream()
            .filter(e -> e.getValue().size() >= 2)
            .map(e -> new DupGroupDTO(
                e.getKey(),
                e.getValue().size(),
                e.getValue().stream().map(this::toDTO).collect(Collectors.toList())
            ))
            .sorted(Comparator.comparingInt(DupGroupDTO::count).reversed())
            .collect(Collectors.toList());

        int dupOrderCount = dupGroups.stream().mapToInt(DupGroupDTO::count).sum();

        log.info("중복 검사 완료: 전체 {}건, {}그룹 {}건 중복", orders.size(), dupGroups.size(), dupOrderCount);

        return ResponseEntity.ok(new DupResultDTO(
            orders.size(),
            dupGroups.size(),
            dupOrderCount,
            dupGroups
        ));
    }

    /**
     * 선택 주문 취소 처리
     * POST /api/processing/duplicate/cancel
     * Body: { "orderNos": ["OMS-...", "OMS-..."] }
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
                opt.get().setOrderStatus("CANCELLED");
                orderRepository.save(opt.get());
                cancelled++;
            }
        }

        return ResponseEntity.ok(Map.of(
            "success", true,
            "cancelled", cancelled,
            "message", cancelled + "건 취소 처리 완료"
        ));
    }

    /**
     * 각 그룹 최신 1건만 남기고 나머지 취소
     * POST /api/processing/duplicate/keep-latest
     * Body: { "criteria": "PHONE_SKU" }
     */
    @PostMapping("/keep-latest")
    @Transactional
    public ResponseEntity<Map<String, Object>> keepLatest(
        @RequestBody Map<String, String> body
    ) {
        Criteria criteria;
        try {
            criteria = Criteria.valueOf(body.getOrDefault("criteria", "PHONE_SKU"));
        } catch (IllegalArgumentException e) {
            criteria = Criteria.PHONE_SKU;
        }

        log.info("최신 1건 유지 처리: criteria={}", criteria);

        List<Order> orders = orderRepository.findAll().stream()
            .filter(o -> !"CANCELLED".equals(o.getOrderStatus()))
            .collect(Collectors.toList());

        orders.forEach(o -> {
            o.getItems().size();
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
            // 가장 최근 주문 1건만 남기고 나머지 취소
            group.sort(Comparator.comparing(
                o -> o.getOrderedAt() != null ? o.getOrderedAt() : o.getCreatedAt(),
                Comparator.nullsLast(Comparator.reverseOrder())
            ));
            for (int i = 1; i < group.size(); i++) {
                group.get(i).setOrderStatus("CANCELLED");
                orderRepository.save(group.get(i));
                cancelled++;
            }
        }

        return ResponseEntity.ok(Map.of(
            "success", true,
            "cancelled", cancelled,
            "message", cancelled + "건 취소 처리 완료 (각 그룹 최신 1건 유지)"
        ));
    }

    // ─── 그룹핑 키 생성 ───────────────────────────────────────────
    private String buildKey(Order o, Criteria criteria) {
        String phone = normalize(o.getRecipientPhone());
        String firstItemCode = o.getItems().isEmpty() ? "" :
            normalize(o.getItems().get(0).getProductCode());
        String firstItemName = o.getItems().isEmpty() ? "" :
            normalize(o.getItems().get(0).getProductName());

        return switch (criteria) {
            case PHONE_SKU ->
                phone + "|" + firstItemCode;
            case PHONE_PRODUCT ->
                phone + "|" + (firstItemName.length() > 10 ? firstItemName.substring(0, 10) : firstItemName);
            case NAME_ADDRESS_SKU -> {
                String addr = normalize(o.getAddress());
                yield normalize(o.getRecipientName()) + "|" +
                    (addr.length() > 15 ? addr.substring(0, 15) : addr) + "|" + firstItemCode;
            }
            case ORDER_NO -> normalize(o.getOrderNo());
        };
    }

    private String normalize(String s) {
        if (s == null) return "";
        return s.replaceAll("[^\\w가-힣]", "").toLowerCase();
    }

    // ─── Order → DTO 변환 ─────────────────────────────────────────
    private DupOrderDTO toDTO(Order o) {
        String productName = o.getItems().isEmpty() ? "" : o.getItems().get(0).getProductName();
        String productCode = o.getItems().isEmpty() ? "" : o.getItems().get(0).getProductCode();
        int    quantity    = o.getItems().isEmpty() ? 0  : o.getItems().get(0).getQuantity();

        return new DupOrderDTO(
            o.getOrderId() != null ? o.getOrderId().toString() : "",
            o.getOrderNo(),
            o.getChannel() != null ? o.getChannel().getChannelName() : "",
            o.getRecipientName(),
            o.getRecipientPhone(),
            o.getAddress(),
            productName,
            productCode,
            quantity,
            o.getOrderStatus(),
            o.getOrderedAt() != null ? o.getOrderedAt().toString() : ""
        );
    }
}
