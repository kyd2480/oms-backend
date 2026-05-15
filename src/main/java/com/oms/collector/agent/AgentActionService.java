package com.oms.collector.agent;

import com.oms.collector.agent.dto.AgentActionProposal;
import com.oms.collector.agent.dto.AgentExecuteResponse;
import com.oms.collector.controller.AllocationController;
import com.oms.collector.controller.CancelController;
import com.oms.collector.controller.CsMemoController;
import com.oms.collector.controller.InvoiceController;
import com.oms.collector.controller.StockMatchingController;
import com.oms.collector.entity.Order;
import com.oms.collector.entity.PrintType;
import com.oms.collector.repository.OrderRepository;
import com.oms.collector.repository.PrintTypeRepository;
import com.oms.collector.service.SabangnetOrderCollectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentActionService {

    private static final Pattern ORDER_PATTERN = Pattern.compile("(OMS-[A-Za-z0-9\\-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern WAREHOUSE_PATTERN = Pattern.compile("(ANYANG|ICHEON_BOX|ICHEON_PCS|BUCHEON)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEMO_PATTERN = Pattern.compile("(?:메모|memo)\\s*[:：]?\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final long TOKEN_TTL_SECONDS = 600;
    private static final ZoneId OMS_ZONE = ZoneId.of("Asia/Seoul");

    private final InvoiceController invoiceController;
    private final AllocationController allocationController;
    private final CancelController cancelController;
    private final StockMatchingController stockMatchingController;
    private final CsMemoController csMemoController;
    private final SabangnetOrderCollectionService sabangnetOrderCollectionService;
    private final OrderRepository orderRepository;
    private final PrintTypeRepository printTypeRepository;

    private final Map<String, StoredAction> pendingActions = new ConcurrentHashMap<>();

    public AgentActionProposal propose(String message, String userName) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        String orderNo = extract(ORDER_PATTERN, message);
        String warehouseCode = extract(WAREHOUSE_PATTERN, message);
        String memoContent = extract(MEMO_PATTERN, message);

        if (orderNo != null && containsAny(normalized,
            "송장 자동", "송장부여", "송장 자동부여", "송장 번호 부여", "송장번호 부여", "송장 번호 등록", "송장번호 등록", "송장 등록", "송장 넣어", "송장 입력",
            "auto assign")) {
            return storeProposal(
                "AUTO_ASSIGN_INVOICE",
                "송장 자동부여 실행",
                orderNo + " 주문에 우체국택배 송장을 자동 부여합니다.",
                "medium",
                Map.of(
                    "orderNo", orderNo,
                    "carrierCode", "POST",
                    "carrierName", "우체국택배"
                )
            );
        }

        if (containsAny(normalized, "전체 송장 자동", "전체송장 자동", "전체 송장부여", "all invoices")) {
            return storeProposal(
                "AUTO_ASSIGN_ALL_INVOICES",
                "전체 송장 자동부여",
                "송장이 없는 CONFIRMED 주문 전체에 우체국택배 송장을 자동 부여합니다.",
                "high",
                Map.of(
                    "carrierCode", "POST",
                    "carrierName", "우체국택배"
                )
            );
        }

        if (containsAny(normalized, "사방넷 주문수집", "사방넷 주문 수집", "사방넷 수집", "주문수집 실행", "주문 수집 실행")) {
            return storeProposal(
                "COLLECT_SABANGNET_ORDERS",
                "사방넷 주문수집 실행",
                "현재 회사코드 기준 사방넷 연동 주문 수집을 즉시 실행합니다.",
                "medium",
                Map.of()
            );
        }

        if (containsAny(normalized, "미출고") && containsAny(normalized, "보류", "보류 처리", "보류설정") && message != null && message.contains("어제")) {
            return storeProposal(
                "HOLD_YESTERDAY_UNSHIPPED",
                "어제 미출고 주문 일괄 보류",
                "어제 주문된 미출고 주문을 일괄 보류 처리합니다.",
                "high",
                Map.of(
                    "date", LocalDate.now(OMS_ZONE).minusDays(1).toString(),
                    "reason", "AI 일괄보류 / 어제 미출고 주문"
                )
            );
        }

        if (orderNo != null && containsAny(normalized, "송장 삭제", "송장삭제", "송장 지워", "delete invoice")) {
            return storeProposal(
                "DELETE_INVOICE",
                "송장 삭제",
                orderNo + " 주문의 송장 정보를 삭제합니다.",
                "medium",
                Map.of("orderNo", orderNo)
            );
        }

        if (containsAny(normalized, "인쇄구분") && containsAny(normalized, "일괄 변경", "일괄변경", "전체 변경", "일괄수정")) {
            PrintTypeChangeSelection selection = resolvePrintTypeChange(message);
            if (selection != null) {
                return storeProposal(
                    "BULK_CHANGE_PRINT_TYPE",
                    "인쇄구분 일괄 변경",
                    selection.source().getName() + " 주문의 인쇄구분을 " + selection.target().getName() + "으로 일괄 변경합니다.",
                    "medium",
                    Map.of(
                        "sourceCode", selection.source().getCode(),
                        "sourceName", selection.source().getName(),
                        "targetCode", selection.target().getCode(),
                        "targetName", selection.target().getName()
                    )
                );
            }
        }

        if (orderNo != null && containsAny(normalized, "발송 취소", "발송취소", "배송 취소", "cancel shipment")) {
            return storeProposal(
                "CANCEL_SHIPMENT",
                "발송 취소 실행",
                orderNo + " 주문의 발송 상태를 취소하고 재고를 복구합니다.",
                "high",
                Map.of("orderNo", orderNo)
            );
        }

        if (orderNo != null && containsAny(normalized, "검수 발송", "발송 처리", "출고 처리", "ship confirm")) {
            Map<String, String> params = new HashMap<>();
            params.put("orderNo", orderNo);
            if (warehouseCode != null) {
                params.put("warehouseCode", warehouseCode.toUpperCase(Locale.ROOT));
                params.put("warehouseName", warehouseDisplayName(warehouseCode));
            }
            return storeProposal(
                "CONFIRM_SHIPMENT",
                "검수 발송 처리",
                orderNo + " 주문을 검수 발송 처리하고 재고를 실차감합니다.",
                "high",
                params
            );
        }

        if (warehouseCode != null && containsAny(normalized, "할당 창고", "창고 설정", "창고를", "설정해줘", "으로 설정", "set warehouse")) {
            return storeProposal(
                "SET_ALLOCATION_WAREHOUSE",
                "할당 창고 변경",
                "현재 할당 창고를 " + warehouseDisplayName(warehouseCode) + "로 변경합니다.",
                "low",
                Map.of(
                    "warehouseCode", warehouseCode.toUpperCase(Locale.ROOT),
                    "warehouseName", warehouseDisplayName(warehouseCode)
                )
            );
        }

        if (orderNo != null && containsAny(normalized, "할당 취소", "예약 해제", "release allocation")) {
            return storeProposal(
                "RELEASE_ALLOCATION",
                "할당 취소",
                orderNo + " 주문의 재고 예약을 해제하고 상태를 PENDING으로 되돌립니다.",
                "medium",
                Map.of("orderNo", orderNo)
            );
        }

        if (orderNo != null && warehouseCode != null && containsAny(normalized, "재고 예약", "예약 실행", "reserve stock")) {
            return storeProposal(
                "RESERVE_STOCK",
                "재고 예약 실행",
                orderNo + " 주문을 " + warehouseDisplayName(warehouseCode) + " 기준으로 예약합니다.",
                "medium",
                Map.of(
                    "orderNo", orderNo,
                    "warehouseCode", warehouseCode.toUpperCase(Locale.ROOT)
                )
            );
        }

        if (orderNo != null && containsAny(normalized, "주문 취소", "배송전 취소", "cancel order")) {
            return storeProposal(
                "CANCEL_ORDER",
                "주문 취소",
                orderNo + " 주문을 배송전 취소 처리합니다.",
                "high",
                Map.of("orderNo", orderNo)
            );
        }

        if (orderNo != null && memoContent != null && containsAny(normalized, "cs 메모", "메모 추가", "memo add")) {
            return storeProposal(
                "ADD_CS_MEMO",
                "CS 메모 추가",
                orderNo + " 주문에 CS 메모를 추가합니다.",
                "low",
                Map.of(
                    "orderNo", orderNo,
                    "content", memoContent,
                    "writer", userName != null ? userName : "AI Agent"
                )
            );
        }

        return null;
    }

    @Transactional
    public AgentExecuteResponse execute(String confirmationToken) {
        clearExpired();
        StoredAction stored = pendingActions.remove(confirmationToken);
        if (stored == null) {
            return new AgentExecuteResponse(false, "실행 토큰이 없거나 만료되었습니다. 다시 제안받아 주세요.", null);
        }

        try {
            ResponseEntity<Map<String, Object>> response = switch (stored.actionType) {
                case "AUTO_ASSIGN_INVOICE" -> invoiceController.autoAssign(stored.params.get("orderNo"), stored.params);
                case "AUTO_ASSIGN_ALL_INVOICES" -> invoiceController.autoAssignAll(stored.params);
                case "DELETE_INVOICE" -> invoiceController.deleteInvoice(stored.params.get("orderNo"));
                case "CANCEL_SHIPMENT" -> invoiceController.cancelShip(stored.params.get("orderNo"));
                case "CONFIRM_SHIPMENT" -> allocationController.confirmAndDeduct(stored.params.get("orderNo"), stored.params);
                case "SET_ALLOCATION_WAREHOUSE" -> allocationController.setWarehouse(stored.params);
                case "RELEASE_ALLOCATION" -> allocationController.release(stored.params.get("orderNo"));
                case "RESERVE_STOCK" -> stockMatchingController.reserve(Map.of(
                    "warehouseCode", stored.params.get("warehouseCode"),
                    "orderNos", java.util.List.of(stored.params.get("orderNo"))
                ));
                case "CANCEL_ORDER" -> cancelController.cancel(stored.params.get("orderNo"), stored.params);
                case "COLLECT_SABANGNET_ORDERS" -> {
                    SabangnetOrderCollectionService.SabangnetCollectResult result = sabangnetOrderCollectionService.collect(null, null);
                    yield ResponseEntity.status(result.success() ? 200 : 400).body(Map.of(
                        "success", result.success(),
                        "message", Objects.toString(result.message(), "사방넷 주문 수집 완료")
                            + " · 수집 " + result.collectedCount() + "건 · 저장 " + result.savedCount() + "건 · 주문생성 " + result.processedCount() + "건"
                    ));
                }
                case "HOLD_YESTERDAY_UNSHIPPED" -> ResponseEntity.ok(executeYesterdayUnshippedHold(stored.params));
                case "BULK_CHANGE_PRINT_TYPE" -> ResponseEntity.ok(executeBulkPrintTypeChange(stored.params));
                case "ADD_CS_MEMO" -> {
                    CsMemoController.MemoCreateRequest req = new CsMemoController.MemoCreateRequest();
                    req.orderNo = stored.params.get("orderNo");
                    req.content = stored.params.get("content");
                    req.writer = stored.params.get("writer");
                    req.csType = "AI";
                    req.csDept = "운영";
                    req.csKind = "메모";
                    req.status = "미처리";
                    ResponseEntity<CsMemoController.MemoDTO> memoRes = csMemoController.save(req);
                    boolean ok = memoRes.getStatusCode().is2xxSuccessful() && memoRes.getBody() != null;
                    yield ResponseEntity.ok(Map.of(
                        "success", ok,
                        "message", ok ? "CS 메모를 추가했습니다." : "CS 메모 추가에 실패했습니다."
                    ));
                }
                default -> ResponseEntity.badRequest().body(Map.of("success", false, "message", "지원하지 않는 액션입니다."));
            };

            Map<String, Object> body = response.getBody() != null ? response.getBody() : Map.of();
            boolean success = Boolean.TRUE.equals(body.get("success"));
            String message = String.valueOf(body.getOrDefault("message", success ? "실행 완료" : "실행 실패"));
            log.info("Agent action executed: type={}, success={}, params={}", stored.actionType, success, stored.params);
            return new AgentExecuteResponse(success, message, stored.actionType);
        } catch (Exception e) {
            log.error("Agent action failed: type={}, params={}", stored.actionType, stored.params, e);
            return new AgentExecuteResponse(false, "실행 중 오류가 발생했습니다: " + e.getMessage(), stored.actionType);
        }
    }

    private AgentActionProposal storeProposal(String actionType, String title, String description, String riskLevel, Map<String, String> params) {
        clearExpired();
        String token = UUID.randomUUID().toString();
        pendingActions.put(token, new StoredAction(actionType, new HashMap<>(params), Instant.now().plusSeconds(TOKEN_TTL_SECONDS)));
        return new AgentActionProposal(actionType, title, description, token, true, riskLevel, params);
    }

    private void clearExpired() {
        Instant now = Instant.now();
        pendingActions.entrySet().removeIf(entry -> entry.getValue().expiresAt.isBefore(now));
    }

    private boolean containsAny(String source, String... needles) {
        for (String needle : needles) {
            if (source.contains(needle)) return true;
        }
        return false;
    }

    private String extract(Pattern pattern, String source) {
        if (source == null) return null;
        Matcher matcher = pattern.matcher(source);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String warehouseDisplayName(String warehouseCode) {
        return switch (warehouseCode.toUpperCase(Locale.ROOT)) {
            case "ANYANG" -> "본사(안양)";
            case "ICHEON_BOX" -> "고백창고(이천)";
            case "ICHEON_PCS" -> "고백피스(이천)";
            case "BUCHEON" -> "부천검수창고";
            default -> warehouseCode;
        };
    }

    private Map<String, Object> executeYesterdayUnshippedHold(Map<String, String> params) {
        LocalDate targetDate = LocalDate.parse(params.getOrDefault("date", LocalDate.now(OMS_ZONE).minusDays(1).toString()));
        String reason = params.getOrDefault("reason", "AI 일괄보류 / 어제 미출고 주문");
        LocalDateTime start = targetDate.atStartOfDay();
        LocalDateTime end = targetDate.atTime(23, 59, 59);

        List<Order> changed = orderRepository.findByDateRange(start, end).stream()
            .filter(order -> order.getOrderStatus() == Order.OrderStatus.PENDING || order.getOrderStatus() == Order.OrderStatus.CONFIRMED)
            .filter(order -> !Boolean.TRUE.equals(order.getShippingHold()))
            .toList();

        changed.forEach(order -> {
            order.setShippingHold(true);
            order.setHoldReason(reason);
        });
        orderRepository.saveAll(changed);

        return Map.of(
            "success", true,
            "message", changed.isEmpty()
                ? targetDate + " 기준 미출고 주문 중 새로 보류할 대상이 없습니다."
                : targetDate + " 기준 미출고 주문 " + changed.size() + "건을 보류 처리했습니다."
        );
    }

    private Map<String, Object> executeBulkPrintTypeChange(Map<String, String> params) {
        String sourceCode = params.get("sourceCode");
        String sourceName = params.get("sourceName");
        String targetCode = params.get("targetCode");
        String targetName = params.get("targetName");

        List<Order> changed = new ArrayList<>(orderRepository.findByPrintTypeCodeAndOrderStatusNot(sourceCode, Order.OrderStatus.CANCELLED));
        changed.forEach(order -> {
            order.setPrintTypeCode(targetCode);
            order.setPrintTypeName(targetName);
        });
        orderRepository.saveAll(changed);

        return Map.of(
            "success", true,
            "message", changed.isEmpty()
                ? sourceName + " 인쇄구분 대상 주문이 없습니다."
                : sourceName + " 주문 " + changed.size() + "건의 인쇄구분을 " + targetName + "으로 변경했습니다."
        );
    }

    private PrintTypeChangeSelection resolvePrintTypeChange(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        List<PrintTypeMatch> matches = new ArrayList<>();
        for (PrintType type : printTypeRepository.findAllByOrderBySortOrderAscNameAsc()) {
            collectPrintTypeMatch(matches, normalized, type, type.getName());
            collectPrintTypeMatch(matches, normalized, type, type.getCode());
        }
        matches.sort((a, b) -> Integer.compare(a.index(), b.index()));

        List<PrintType> distinct = matches.stream()
            .map(PrintTypeMatch::type)
            .distinct()
            .toList();

        if (distinct.size() < 2) {
            return null;
        }
        return new PrintTypeChangeSelection(distinct.get(0), distinct.get(1));
    }

    private void collectPrintTypeMatch(List<PrintTypeMatch> matches, String normalizedMessage, PrintType type, String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        int index = normalizedMessage.indexOf(token.toLowerCase(Locale.ROOT));
        if (index >= 0) {
            matches.add(new PrintTypeMatch(type, index));
        }
    }

    private record StoredAction(
        String actionType,
        Map<String, String> params,
        Instant expiresAt
    ) {}

    private record PrintTypeMatch(PrintType type, int index) {}

    private record PrintTypeChangeSelection(PrintType source, PrintType target) {}
}
