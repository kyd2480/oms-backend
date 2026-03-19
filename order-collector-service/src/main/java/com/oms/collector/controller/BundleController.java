package com.oms.collector.controller;

import com.oms.collector.entity.BundleGroup;
import com.oms.collector.entity.Order;
import com.oms.collector.entity.OrderItem;
import com.oms.collector.repository.BundleGroupRepository;
import com.oms.collector.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 묶음 정리 컨트롤러
 *
 * GET  /api/bundle/detect          - 묶음 가능 그룹 탐지
 * POST /api/bundle/confirm         - 묶음 확정 (DB 저장)
 * POST /api/bundle/confirm-all     - 전체 묶음 일괄 확정
 * GET  /api/bundle/list            - 확정된 묶음 목록 조회
 * POST /api/bundle/release/{id}    - 묶음 해제
 */
@Slf4j
@RestController
@RequestMapping("/api/bundle")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BundleController {

    private final OrderRepository      orderRepository;
    private final BundleGroupRepository bundleGroupRepository;

    // ─── DTO ─────────────────────────────────────────────────────

    public static class OrderSummaryDTO {
        public String orderNo;
        public String channelName;
        public String orderedAt;
        public List<String> productNames;
        public String totalAmount;

        public OrderSummaryDTO(Order o) {
            this.orderNo     = o.getOrderNo();
            this.channelName = o.getChannel() != null ? o.getChannel().getChannelName() : "";
            this.orderedAt   = o.getOrderedAt() != null ? o.getOrderedAt().toString() : "";
            this.totalAmount = o.getTotalAmount() != null ? o.getTotalAmount().toPlainString() : "0";
            this.productNames = o.getItems().stream()
                .map(OrderItem::getProductName)
                .collect(Collectors.toList());
        }
    }

    public static class BundleCandidateDTO {
        public String bundleKey;
        public String recipientName;
        public String recipientPhone;
        public String address;
        public int orderCount;
        public List<OrderSummaryDTO> orders;
        public boolean alreadyBundled;

        public BundleCandidateDTO(String key, List<Order> orders, boolean alreadyBundled) {
            Order first = orders.get(0);
            this.bundleKey      = key;
            this.recipientName  = first.getRecipientName();
            this.recipientPhone = first.getRecipientPhone();
            this.address        = first.getAddress()
                + (first.getAddressDetail() != null ? " " + first.getAddressDetail() : "");
            this.orderCount     = orders.size();
            this.orders         = orders.stream().map(OrderSummaryDTO::new).collect(Collectors.toList());
            this.alreadyBundled = alreadyBundled;
        }
    }

    public static class DetectResultDTO {
        public int totalOrders;
        public int bundleCandidates;
        public int bundleableOrders;
        public List<BundleCandidateDTO> candidates;

        public DetectResultDTO(int total, List<BundleCandidateDTO> candidates) {
            this.totalOrders       = total;
            this.bundleCandidates  = candidates.size();
            this.bundleableOrders  = candidates.stream().mapToInt(c -> c.orderCount).sum();
            this.candidates        = candidates;
        }
    }

    public static class BundleGroupDTO {
        public String bundleId;
        public String bundleKey;
        public String representativeOrderNo;
        public String[] orderNos;
        public int orderCount;
        public String recipientName;
        public String recipientPhone;
        public String address;
        public String status;
        public String memo;
        public String createdAt;

        public BundleGroupDTO(BundleGroup g) {
            this.bundleId              = g.getBundleId().toString();
            this.bundleKey             = g.getBundleKey();
            this.representativeOrderNo = g.getRepresentativeOrderNo();
            this.orderNos              = g.getOrderNoArray();
            this.orderCount            = g.getOrderCount();
            this.recipientName         = g.getRecipientName();
            this.recipientPhone        = g.getRecipientPhone();
            this.address               = g.getAddress();
            this.status                = g.getStatus().name();
            this.memo                  = g.getMemo();
            this.createdAt             = g.getCreatedAt() != null ? g.getCreatedAt().toString() : "";
        }
    }

    // ─── 묶음 가능 그룹 탐지 ─────────────────────────────────────

    /**
     * 묶음 가능 그룹 탐지
     * GET /api/bundle/detect
     */
    @GetMapping("/detect")
    @Transactional(readOnly = true)
    public ResponseEntity<DetectResultDTO> detect() {
        log.info("묶음 그룹 탐지 시작");

        // 취소되지 않은 주문 전체 조회
        List<Order> orders = orderRepository.findAll().stream()
            .filter(o -> o.getOrderStatus() != Order.OrderStatus.CANCELLED)
            .collect(Collectors.toList());

        // Lazy loading 초기화
        orders.forEach(o -> {
            o.getItems().size();
            if (o.getChannel() != null) o.getChannel().getChannelName();
        });

        // 수취인+연락처+주소 기준 그룹핑
        Map<String, List<Order>> grouped = new LinkedHashMap<>();
        for (Order o : orders) {
            String key = buildBundleKey(o);
            if (key.isBlank()) continue;
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(o);
        }

        // 기존 묶음 키 목록
        Set<String> existingKeys = bundleGroupRepository
            .findByStatusOrderByCreatedAtDesc(BundleGroup.BundleStatus.BUNDLED)
            .stream()
            .map(BundleGroup::getBundleKey)
            .collect(Collectors.toSet());

        // 2건 이상인 그룹만 후보
        List<BundleCandidateDTO> candidates = grouped.entrySet().stream()
            .filter(e -> e.getValue().size() >= 2)
            .map(e -> new BundleCandidateDTO(
                e.getKey(),
                e.getValue(),
                existingKeys.contains(e.getKey())
            ))
            .sorted(Comparator.comparingInt((BundleCandidateDTO c) -> c.orderCount).reversed())
            .collect(Collectors.toList());

        log.info("묶음 탐지 완료: 전체 {}건, {}그룹", orders.size(), candidates.size());
        return ResponseEntity.ok(new DetectResultDTO(orders.size(), candidates));
    }

    // ─── 묶음 확정 ───────────────────────────────────────────────

    /**
     * 선택 그룹 묶음 확정
     * POST /api/bundle/confirm
     * Body: { "bundleKey": "...", "orderNos": ["OMS-...", ...], "memo": "" }
     */
    @PostMapping("/confirm")
    @Transactional
    public ResponseEntity<Map<String, Object>> confirm(
        @RequestBody Map<String, Object> body
    ) {
        String bundleKey = (String) body.get("bundleKey");
        @SuppressWarnings("unchecked")
        List<String> orderNos = (List<String>) body.get("orderNos");
        String memo = (String) body.getOrDefault("memo", "");

        if (bundleKey == null || orderNos == null || orderNos.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "필수값 누락"));
        }

        // 주문 조회 후 최신순 정렬 → 첫 번째가 대표(최신) 주문
        List<Order> orders = orderNos.stream()
            .map(no -> orderRepository.findByOrderNo(no).orElse(null))
            .filter(java.util.Objects::nonNull)
            .sorted(Comparator.comparing(
                o -> o.getOrderedAt() != null ? o.getOrderedAt() : o.getCreatedAt(),
                Comparator.nullsLast(Comparator.reverseOrder())
            ))
            .collect(Collectors.toList());

        if (orders.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "주문을 찾을 수 없음"));
        }

        Order representative = orders.get(0);
        String representativeNo = representative.getOrderNo();

        // 대표 주문 제외 나머지 취소
        int cancelled = 0;
        for (int i = 1; i < orders.size(); i++) {
            orders.get(i).setOrderStatus(Order.OrderStatus.CANCELLED);
            orderRepository.save(orders.get(i));
            cancelled++;
        }

        // 묶음 그룹 저장/업데이트
        Optional<BundleGroup> existing = bundleGroupRepository.findByBundleKey(bundleKey);
        BundleGroup bundle;

        if (existing.isPresent()) {
            bundle = existing.get();
            bundle.setRepresentativeOrderNo(representativeNo);
            bundle.setOrderNos(String.join(",", orderNos));
            bundle.setStatus(BundleGroup.BundleStatus.BUNDLED);
            bundle.setMemo(memo);
        } else {
            bundle = BundleGroup.builder()
                .bundleKey(bundleKey)
                .representativeOrderNo(representativeNo)
                .orderNos(String.join(",", orderNos))
                .recipientName(representative.getRecipientName())
                .recipientPhone(representative.getRecipientPhone())
                .address(representative.getAddress())
                .status(BundleGroup.BundleStatus.BUNDLED)
                .memo(memo)
                .build();
        }
        bundle.setConfirmedAt(LocalDateTime.now());
        bundleGroupRepository.save(bundle);

        log.info("묶음 확정: key={}, 대표={}, {}건 취소", bundleKey, representativeNo, cancelled);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "representativeOrderNo", representativeNo,
            "cancelled", cancelled,
            "message", "묶음 확정 완료 (대표: " + representativeNo + ", " + cancelled + "건 취소)"
        ));
    }

    /**
     * 전체 묶음 일괄 확정
     * POST /api/bundle/confirm-all
     */
    @PostMapping("/confirm-all")
    @Transactional
    public ResponseEntity<Map<String, Object>> confirmAll() {
        log.info("전체 묶음 일괄 확정 시작");

        List<Order> orders = orderRepository.findAll().stream()
            .filter(o -> o.getOrderStatus() != Order.OrderStatus.CANCELLED)
            .collect(Collectors.toList());

        orders.forEach(o -> {
            o.getItems().size();
            if (o.getChannel() != null) o.getChannel().getChannelName();
        });

        Map<String, List<Order>> grouped = new LinkedHashMap<>();
        for (Order o : orders) {
            String key = buildBundleKey(o);
            if (key.isBlank()) continue;
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(o);
        }

        int confirmed = 0;
        for (Map.Entry<String, List<Order>> entry : grouped.entrySet()) {
            if (entry.getValue().size() < 2) continue;

            List<String> orderNos = entry.getValue().stream()
                .map(Order::getOrderNo)
                .collect(Collectors.toList());

            Optional<BundleGroup> existing = bundleGroupRepository.findByBundleKey(entry.getKey());
            BundleGroup bundle;

            // 최신순 정렬 → 대표 주문 = 최신 1건
            List<Order> groupOrders = entry.getValue().stream()
                .sorted(Comparator.comparing(
                    o -> o.getOrderedAt() != null ? o.getOrderedAt() : o.getCreatedAt(),
                    Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .collect(Collectors.toList());

            Order rep = groupOrders.get(0);

            // 대표 제외 나머지 취소
            for (int i = 1; i < groupOrders.size(); i++) {
                groupOrders.get(i).setOrderStatus(Order.OrderStatus.CANCELLED);
                orderRepository.save(groupOrders.get(i));
            }

            if (existing.isPresent()) {
                bundle = existing.get();
                bundle.setRepresentativeOrderNo(rep.getOrderNo());
                bundle.setOrderNos(String.join(",", orderNos));
                bundle.setStatus(BundleGroup.BundleStatus.BUNDLED);
            } else {
                bundle = BundleGroup.builder()
                    .bundleKey(entry.getKey())
                    .representativeOrderNo(rep.getOrderNo())
                    .orderNos(String.join(",", orderNos))
                    .recipientName(rep.getRecipientName())
                    .recipientPhone(rep.getRecipientPhone())
                    .address(rep.getAddress())
                    .status(BundleGroup.BundleStatus.BUNDLED)
                    .build();
            }
            bundle.setConfirmedAt(LocalDateTime.now());
            bundleGroupRepository.save(bundle);
            confirmed++;
        }

        log.info("전체 묶음 확정 완료: {}그룹", confirmed);
        return ResponseEntity.ok(Map.of("success", true, "confirmed", confirmed,
            "message", confirmed + "그룹 묶음 확정 완료"));
    }

    // ─── 묶음 목록 조회 ──────────────────────────────────────────

    /**
     * 확정된 묶음 목록
     * GET /api/bundle/list
     */
    @GetMapping("/list")
    public ResponseEntity<List<BundleGroupDTO>> list() {
        List<BundleGroupDTO> result = bundleGroupRepository.findAllByOrderByCreatedAtDesc()
            .stream()
            .map(BundleGroupDTO::new)
            .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    /**
     * 묶음 해제
     * POST /api/bundle/release/{bundleId}
     */
    @PostMapping("/release/{bundleId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> release(@PathVariable UUID bundleId) {
        return bundleGroupRepository.findById(bundleId)
            .map(bundle -> {
                bundle.setStatus(BundleGroup.BundleStatus.RELEASED);
                bundleGroupRepository.save(bundle);
                log.info("묶음 해제: {}", bundleId);
                return ResponseEntity.ok(Map.<String, Object>of("success", true, "message", "묶음 해제 완료"));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    // ─── 유틸 ────────────────────────────────────────────────────

    private String buildBundleKey(Order o) {
        String name  = norm(o.getRecipientName());
        String phone = norm(o.getRecipientPhone());
        String addr  = norm(o.getAddress());
        if (name.isBlank() || phone.isBlank() || addr.isBlank()) return "";
        return name + "|" + phone + "|" + addr;
    }

    private String norm(String s) {
        if (s == null) return "";
        return s.replaceAll("[^\\w가-힣]", "").toLowerCase();
    }
}
