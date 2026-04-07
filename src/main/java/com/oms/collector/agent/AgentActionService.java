package com.oms.collector.agent;

import com.oms.collector.agent.dto.AgentActionProposal;
import com.oms.collector.agent.dto.AgentExecuteResponse;
import com.oms.collector.controller.AllocationController;
import com.oms.collector.controller.CancelController;
import com.oms.collector.controller.CsMemoController;
import com.oms.collector.controller.InvoiceController;
import com.oms.collector.controller.StockMatchingController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentActionService {

    private static final Pattern ORDER_PATTERN = Pattern.compile("(OMS-[A-Za-z0-9\\-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern WAREHOUSE_PATTERN = Pattern.compile("\\b(ANYANG|ICHEON_BOX|ICHEON_PCS|BUCHEON)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEMO_PATTERN = Pattern.compile("(?:메모|memo)\\s*[:：]?\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final long TOKEN_TTL_SECONDS = 600;

    private final InvoiceController invoiceController;
    private final AllocationController allocationController;
    private final CancelController cancelController;
    private final StockMatchingController stockMatchingController;
    private final CsMemoController csMemoController;

    private final Map<String, StoredAction> pendingActions = new ConcurrentHashMap<>();

    public AgentActionProposal propose(String message, String userName) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        String orderNo = extract(ORDER_PATTERN, message);
        String warehouseCode = extract(WAREHOUSE_PATTERN, message);
        String memoContent = extract(MEMO_PATTERN, message);

        if (orderNo != null && containsAny(normalized, "송장 자동", "송장부여", "송장 자동부여", "auto assign")) {
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

        if (orderNo != null && containsAny(normalized, "송장 삭제", "송장삭제", "송장 지워", "delete invoice")) {
            return storeProposal(
                "DELETE_INVOICE",
                "송장 삭제",
                orderNo + " 주문의 송장 정보를 삭제합니다.",
                "medium",
                Map.of("orderNo", orderNo)
            );
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

        if (warehouseCode != null && containsAny(normalized, "할당 창고", "창고 설정", "set warehouse")) {
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

    private record StoredAction(
        String actionType,
        Map<String, String> params,
        Instant expiresAt
    ) {}
}
