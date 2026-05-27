package com.oms.collector.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oms.collector.agent.dto.AgentActionProposal;
import com.oms.collector.agent.dto.AgentChatMessage;
import com.oms.collector.agent.dto.AgentChatRequest;
import com.oms.collector.agent.dto.AgentChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class OmsAgentService {

    private static final String RESPONSES_API = "https://api.openai.com/v1/responses";
    private static final long DEFAULT_OPENAI_TIMEOUT_SECONDS = 90;
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final ZoneId OMS_ZONE = ZoneId.of("Asia/Seoul");
    private static final Pattern DATE_PATTERN = Pattern.compile("(20\\d{2})[.\\-/년\\s]+(\\d{1,2})[.\\-/월\\s]+(\\d{1,2})");
    private static final Pattern ORDER_TOKEN_PATTERN = Pattern.compile("(?i)(oms[-_]?\\d{6,}(?:[-_]?\\d+)?|[a-z]{2,}[-_][a-z0-9_-]*\\d[a-z0-9_-]*|\\d{6,})");
    private static final String SYSTEM_PROMPT = """
        너는 OMS 운영 도우미다.
        목표는 한국어로 짧고 실무적으로 답하는 것이다.
        반드시 현재 OMS 도구로 확인한 사실만 단정적으로 말해라.
        데이터가 부족하면 추정이라고 명시해라.
        일상적인 인사나 간단한 대화에는 자연스럽게 짧게 답해도 된다.
        실행형 작업 요청은 직접 단정 실행하지 말고, 실행 제안과 승인 필요 여부를 분명히 설명해라.
        raw json, key=value 나열, 내부 파라미터명(keyword, status, limit 등) 출력은 금지한다.
        답변은 최대 6줄 안쪽으로 작성해라.
        답변 형식:
        - 첫 줄에 결론
        - 필요할 때만 2~4개의 짧은 항목
        - 숫자와 상태명은 읽기 쉬운 한국어 문장으로 정리
        """;

    private final OmsAgentToolService toolService;
    private final AgentActionService agentActionService;
    private final ObjectMapper objectMapper;

    @Value("${openai.api-key:}")
    private String apiKey;

    @Value("${openai.model:gpt-5-mini}")
    private String model;

    @Value("${openai.agent.enabled:true}")
    private boolean agentEnabled;

    @Value("${openai.timeout-seconds:" + DEFAULT_OPENAI_TIMEOUT_SECONDS + "}")
    private long openaiTimeoutSeconds;

    @Value("${openai.max-output-tokens:500}")
    private int maxOutputTokens;

    @Value("${openai.reasoning-effort:minimal}")
    private String reasoningEffort;

    @Value("${openai.text-verbosity:low}")
    private String textVerbosity;

    public AgentChatResponse chat(AgentChatRequest request) {
        List<String> warnings = new ArrayList<>();
        List<Map<String, Object>> toolCalls = new ArrayList<>();

        if (!agentEnabled) {
            return new AgentChatResponse(false, "AI 에이전트가 비활성화되어 있습니다. `openai.agent.enabled=true`로 설정하세요.", model, false, null, toolCalls, warnings);
        }

        if (request == null || blank(request.message())) {
            return new AgentChatResponse(false, "질문이 비어 있습니다.", model, !blank(apiKey), null, toolCalls, warnings);
        }

        AgentActionProposal proposedAction = agentActionService.propose(request.message(), request.userName());
        if (proposedAction != null) {
            return new AgentChatResponse(
                true,
                formatActionProposalMessage(proposedAction),
                model,
                true,
                proposedAction,
                toolCalls,
                warnings
            );
        }

        AgentChatResponse direct = handleDirectQuery(request, warnings, null);
        if (direct != null) {
            return direct;
        }

        if (blank(apiKey)) {
            warnings.add("OPENAI_API_KEY가 설정되지 않아 AI 응답 대신 연결 안내만 제공합니다.");
            return new AgentChatResponse(
                false,
                "AI 에이전트 연결 전입니다. 서버 환경변수 `OPENAI_API_KEY`를 설정하면 주문/재고 요약형 질의를 처리할 수 있습니다.",
                model,
                false,
                null,
                toolCalls,
                warnings
            );
        }

        Duration openaiTimeout = openAiTimeout();
        HttpClient httpClient = HttpClient.create()
            .responseTimeout(openaiTimeout);

        WebClient client = WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();

        try {
            log.info("Agent request started: user={}, model={}", request.userName(), model);
            JsonNode response = createResponse(client, buildInitialPayload(request), openaiTimeout);
            for (int i = 0; i < 6 && hasFunctionCalls(response); i++) {
                log.info("Agent tool round {} started", i + 1);
                ArrayNode toolOutputs = objectMapper.createArrayNode();
                for (JsonNode node : response.path("output")) {
                    if (!"function_call".equals(node.path("type").asText())) {
                        continue;
                    }
                    String name = node.path("name").asText();
                    String callId = node.path("call_id").asText();
                    JsonNode args = parseJson(node.path("arguments").asText("{}"));
                    Map<String, Object> toolResult = executeTool(name, args);
                    log.info("Agent tool executed: name={}, args={}", name, args);
                    toolCalls.add(Map.of("name", name, "arguments", objectMapper.convertValue(args, Map.class)));

                    ObjectNode output = toolOutputs.addObject();
                    output.put("type", "function_call_output");
                    output.put("call_id", callId);
                    output.put("output", objectMapper.writeValueAsString(toolResult));
                }

                ObjectNode nextPayload = objectMapper.createObjectNode();
                nextPayload.put("model", model);
                nextPayload.put("previous_response_id", response.path("id").asText());
                nextPayload.put("instructions", SYSTEM_PROMPT);
                nextPayload.set("input", toolOutputs);
                applyResponseTuning(nextPayload);
                response = createResponse(client, nextPayload, openaiTimeout);
            }

            String answer = extractText(response);
            if (blank(answer)) {
                warnings.add("모델 응답이 비어 있어 기본 메시지로 대체했습니다.");
                answer = fallbackAnswer(request.message(), toolCalls);
            }
            if (looksLikeRawJson(answer)) {
                warnings.add("모델이 내부 JSON 형식으로 응답해 기본 안내로 대체했습니다.");
                answer = fallbackAnswer(request.message(), toolCalls);
            }

            log.info("Agent request completed: toolCalls={}", toolCalls.size());
            return new AgentChatResponse(true, answer, model, true, proposedAction, toolCalls, warnings);
        } catch (WebClientResponseException e) {
            String detail = extractErrorDetail(e.getResponseBodyAsString());
            log.error("OpenAI API error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            warnings.add("OpenAI API 호출이 실패했습니다.");
            return new AgentChatResponse(
                false,
                "OpenAI API 호출 실패 (" + e.getStatusCode().value() + "): " + detail,
                model,
                true,
                null,
                toolCalls,
                warnings
            );
        } catch (Exception e) {
            log.error("Agent chat failed", e);
            warnings.add("에이전트 처리 중 예외가 발생했습니다.");
            return new AgentChatResponse(false, formatAgentExceptionMessage(e), model, true, null, toolCalls, warnings);
        }
    }

    private JsonNode createResponse(WebClient client, ObjectNode payload, Duration timeout) {
        return client.post()
            .uri(RESPONSES_API)
            .bodyValue(payload)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .timeout(timeout)
            .block();
    }

    private ObjectNode buildInitialPayload(AgentChatRequest request) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", model);
        payload.put("instructions", SYSTEM_PROMPT);
        applyResponseTuning(payload);
        payload.set("input", objectMapper.getNodeFactory().textNode(buildTranscript(request)));
        payload.set("tools", buildTools());
        return payload;
    }

    private void applyResponseTuning(ObjectNode payload) {
        payload.put("max_output_tokens", Math.max(200, Math.min(maxOutputTokens, 1200)));

        if (!blank(reasoningEffort)) {
            ObjectNode reasoning = objectMapper.createObjectNode();
            reasoning.put("effort", reasoningEffort.trim());
            payload.set("reasoning", reasoning);
        }

        if (!blank(textVerbosity)) {
            ObjectNode text = objectMapper.createObjectNode();
            text.put("verbosity", textVerbosity.trim());
            payload.set("text", text);
        }
    }

    private Duration openAiTimeout() {
        return Duration.ofSeconds(Math.max(30, Math.min(openaiTimeoutSeconds, 180)));
    }

    private String formatAgentExceptionMessage(Exception e) {
        String message = e.getMessage() != null ? e.getMessage() : "";
        if (message.contains("TimeoutException") || message.contains("Did not observe any item")) {
            return "OpenAI 응답 시간이 초과되었습니다. 잠시 후 다시 시도하거나 질문을 더 짧게 입력해 주세요.";
        }
        return "에이전트 처리 중 오류가 발생했습니다: " + message;
    }

    private ArrayNode buildTools() {
        ArrayNode tools = objectMapper.createArrayNode();
        tools.add(functionTool(
            "get_order_overview",
            "기간별 주문 현황과 상위 판매처를 조회한다. period는 today, 7d, 30d 중 하나다.",
            """
                {
                  "type": "object",
                  "properties": {
                    "period": {
                      "type": "string",
                      "enum": ["today", "7d", "30d"]
                    }
                  },
                  "required": ["period"],
                  "additionalProperties": false
                }
                """
        ));
        tools.add(functionTool(
            "search_orders",
            "주문번호, 송장번호, 수취인, 고객명, 판매처명으로 주문을 조회한다. 결과에는 송장번호(trackingNo)와 택배사(carrierName)가 포함된다. status는 ALL 또는 PENDING, CONFIRMED, SHIPPED, CANCELLED를 사용한다.",
            """
                {
                  "type": "object",
                  "properties": {
                    "keyword": { "type": "string" },
                    "status": { "type": "string" },
                    "limit": { "type": "integer", "minimum": 1, "maximum": 20 }
                  },
                  "required": ["keyword"],
                  "additionalProperties": false
                }
                """
        ));
        tools.add(functionTool(
            "get_shipment_stats",
            "출고일 기준 발송 완료 주문 건수와 수량을 조회한다. 출고일은 SHIPPED 주문의 updatedAt 기준이다.",
            """
                {
                  "type": "object",
                  "properties": {
                    "startDate": { "type": "string" },
                    "endDate": { "type": "string" },
                    "limit": { "type": "integer", "minimum": 1, "maximum": 50 }
                  },
                  "required": ["startDate", "endDate"],
                  "additionalProperties": false
                }
                """
        ));
        tools.add(functionTool(
            "get_claim_overview",
            "반품 또는 교환 접수 현황을 조회한다. claimType은 ALL, RETURN, EXCHANGE 중 하나다.",
            """
                {
                  "type": "object",
                  "properties": {
                    "claimType": { "type": "string", "enum": ["ALL", "RETURN", "EXCHANGE"] },
                    "startDate": { "type": "string" },
                    "endDate": { "type": "string" },
                    "limit": { "type": "integer", "minimum": 1, "maximum": 50 }
                  },
                  "required": ["claimType", "startDate", "endDate"],
                  "additionalProperties": false
                }
                """
        ));
        tools.add(functionTool(
            "get_inventory_overview",
            "재고 총괄 현황, 품절 수량, 위험 상품 목록을 조회한다.",
            """
                {
                  "type": "object",
                  "properties": {},
                  "additionalProperties": false
                }
                """
        ));
        tools.add(functionTool(
            "get_invoice_pending_overview",
            "송장 입력 대기 또는 송장 미발급 주문 건수를 조회한다.",
            """
                {
                  "type": "object",
                  "properties": {},
                  "additionalProperties": false
                }
                """
        ));
        tools.add(functionTool(
            "get_operational_status_overview",
            "미매칭, 재고 할당 완료, 송장 입력 대기, 검수 대기, 발송 완료 등 운영 상태별 건수를 조회한다.",
            """
                {
                  "type": "object",
                  "properties": {},
                  "additionalProperties": false
                }
                """
        ));
        tools.add(functionTool(
            "search_products",
            "상품명, SKU, 바코드 기준으로 상품을 조회한다.",
            """
                {
                  "type": "object",
                  "properties": {
                    "keyword": { "type": "string" },
                    "limit": { "type": "integer", "minimum": 1, "maximum": 20 }
                  },
                  "required": ["keyword"],
                  "additionalProperties": false
                }
                """
        ));
        tools.add(functionTool(
            "get_top_products_by_channel",
            "특정 기간과 판매처 기준으로 주문량이 많은 상품 상위 목록을 조회한다.",
            """
                {
                  "type": "object",
                  "properties": {
                    "startDate": { "type": "string" },
                    "endDate": { "type": "string" },
                    "channelKeyword": { "type": "string" },
                    "limit": { "type": "integer", "minimum": 1, "maximum": 10 }
                  },
                  "required": ["startDate", "endDate", "channelKeyword"],
                  "additionalProperties": false
                }
                """
        ));
        tools.add(functionTool(
            "search_orders_by_print_type",
            "인쇄구분명 또는 인쇄구분코드 기준으로 주문 목록을 조회한다.",
            """
                {
                  "type": "object",
                  "properties": {
                    "printTypeKeyword": { "type": "string" },
                    "limit": { "type": "integer", "minimum": 1, "maximum": 20 }
                  },
                  "required": ["printTypeKeyword"],
                  "additionalProperties": false
                }
                """
        ));
        return tools;
    }

    private ObjectNode functionTool(String name, String description, String schema) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "function");
        node.put("name", name);
        node.put("description", description);
        node.set("parameters", parseJson(schema));
        return node;
    }

    private Map<String, Object> executeTool(String name, JsonNode args) {
        return switch (name) {
            case "get_order_overview" -> toolService.getOrderOverview(args.path("period").asText("7d"));
            case "search_orders" -> toolService.searchOrders(
                args.path("keyword").asText(""),
                args.path("status").asText("ALL"),
                args.has("limit") ? args.path("limit").asInt(10) : 10
            );
            case "get_shipment_stats" -> toolService.getShipmentStats(
                LocalDate.parse(args.path("startDate").asText(LocalDate.now(OMS_ZONE).toString())),
                LocalDate.parse(args.path("endDate").asText(args.path("startDate").asText(LocalDate.now(OMS_ZONE).toString()))),
                args.has("limit") ? args.path("limit").asInt(10) : 10
            );
            case "get_claim_overview" -> toolService.getClaimOverview(
                args.path("claimType").asText("ALL"),
                LocalDate.parse(args.path("startDate").asText(LocalDate.now(OMS_ZONE).toString())),
                LocalDate.parse(args.path("endDate").asText(args.path("startDate").asText(LocalDate.now(OMS_ZONE).toString()))),
                args.has("limit") ? args.path("limit").asInt(10) : 10
            );
            case "get_inventory_overview" -> toolService.getInventoryOverview();
            case "get_invoice_pending_overview" -> toolService.getInvoicePendingOverview();
            case "get_operational_status_overview" -> toolService.getOperationalStatusOverview();
            case "search_products" -> toolService.searchProducts(
                args.path("keyword").asText(""),
                args.has("limit") ? args.path("limit").asInt(10) : 10
            );
            case "get_top_products_by_channel" -> toolService.getTopProductsByChannel(
                LocalDate.parse(args.path("startDate").asText(LocalDate.now(OMS_ZONE).minusDays(29).toString())),
                LocalDate.parse(args.path("endDate").asText(LocalDate.now(OMS_ZONE).toString())),
                args.path("channelKeyword").asText(""),
                args.has("limit") ? args.path("limit").asInt(3) : 3
            );
            case "search_orders_by_print_type" -> toolService.searchOrdersByPrintType(
                args.path("printTypeKeyword").asText(""),
                args.has("limit") ? args.path("limit").asInt(10) : 10
            );
            default -> {
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("error", "Unknown tool: " + name);
                yield error;
            }
        };
    }

    private String buildTranscript(AgentChatRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("사용자명: ").append(blank(request.userName()) ? "unknown" : request.userName()).append('\n');
        sb.append("대화 기록:\n");
        List<AgentChatMessage> history = request.history() != null ? request.history() : List.of();
        int start = Math.max(0, history.size() - 8);
        for (int i = start; i < history.size(); i++) {
            AgentChatMessage item = history.get(i);
            if (item == null || blank(item.content())) {
                continue;
            }
            sb.append("- ").append(blank(item.role()) ? "user" : item.role()).append(": ").append(item.content()).append('\n');
        }
        sb.append("- user: ").append(request.message());
        return sb.toString();
    }

    private boolean hasFunctionCalls(JsonNode response) {
        for (JsonNode node : response.path("output")) {
            if ("function_call".equals(node.path("type").asText())) {
                return true;
            }
        }
        return false;
    }

    private String extractText(JsonNode response) {
        if (response.hasNonNull("output_text") && !response.path("output_text").asText().isBlank()) {
            return response.path("output_text").asText();
        }
        for (JsonNode output : response.path("output")) {
            if (!"message".equals(output.path("type").asText())) {
                continue;
            }
            for (JsonNode content : output.path("content")) {
                String type = content.path("type").asText();
                if ("output_text".equals(type) || "text".equals(type)) {
                    return content.path("text").asText("");
                }
            }
        }
        return "";
    }

    private JsonNode parseJson(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private String extractErrorDetail(String rawBody) {
        if (blank(rawBody)) {
            return "응답 본문이 없습니다. API 키, 모델명, 네트워크 설정을 확인하세요.";
        }
        JsonNode body = parseJson(rawBody);
        String message = body.path("error").path("message").asText("");
        if (!blank(message)) {
            return message;
        }
        return rawBody;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private boolean looksLikeRawJson(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}"))
            || (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }

    private boolean isOperationalStatusCountQuestion(String message) {
        if (!containsAny(message,
            "미매칭", "상품 미매칭", "매칭대기", "매칭 대기",
            "재고할당", "재고 할당", "할당완료", "할당 완료",
            "송장입력대기", "송장 입력 대기", "송장대기", "송장 대기", "송장 미발급", "미발급", "송장 미입력",
            "검수대기", "검수 대기", "발송대기", "발송 대기",
            "발송완료", "발송 완료", "출고완료", "출고 완료",
            "보류", "작업 현황", "운영 현황", "처리 현황")) {
            return false;
        }
        return containsAny(message, "몇건", "몇 건", "건수", "몇개", "몇 개", "얼마", "현황", "조회", "알려", "요약");
    }

    private AgentChatResponse handleDirectQuery(AgentChatRequest request, List<String> warnings, AgentActionProposal proposedAction) {
        String message = request.message() != null ? request.message().toLowerCase(Locale.ROOT) : "";
        List<Map<String, Object>> toolCalls = new ArrayList<>();

        if (isGreetingMessage(message)) {
            return new AgentChatResponse(true, buildGreetingReply(request.message()), model, !blank(apiKey), proposedAction, toolCalls, warnings);
        }

        if (containsAny(message, "무슨 작업", "뭐 할 수", "어떤 작업", "가능한 작업", "도움말", "사용법")) {
            return new AgentChatResponse(true, buildCapabilityReply(), model, !blank(apiKey), proposedAction, toolCalls, warnings);
        }

        if (containsAny(message, "최근 주문", "최근 주문건", "최근 주문 보여", "latest orders")) {
            Map<String, Object> result = toolService.searchOrders("", "ALL", 10);
            toolCalls.add(Map.of("name", "search_orders", "arguments", Map.of("keyword", "", "status", "ALL", "limit", 10)));
            return new AgentChatResponse(true, appendActionGuide(formatRecentOrders(result), proposedAction), model, true, proposedAction, toolCalls, warnings);
        }

        String invoiceOrderKeyword = extractInvoiceOrderKeyword(message);
        if (invoiceOrderKeyword != null) {
            Map<String, Object> result = toolService.searchOrders(invoiceOrderKeyword, "ALL", 5);
            toolCalls.add(Map.of("name", "search_orders", "arguments", Map.of("keyword", invoiceOrderKeyword, "status", "ALL", "limit", 5)));
            return new AgentChatResponse(true, appendActionGuide(formatOrderInvoiceResult(invoiceOrderKeyword, result), proposedAction), model, true, proposedAction, toolCalls, warnings);
        }

        if (containsAny(message, "송장 미입력", "송장 없는", "송장 누락", "송장 안 들어간") &&
            !containsAny(message, "몇건", "몇 건", "건수", "몇개", "몇 개", "얼마")) {
            Map<String, Object> result = toolService.searchOrders("", "CONFIRMED", 20);
            toolCalls.add(Map.of("name", "search_orders", "arguments", Map.of("keyword", "", "status", "CONFIRMED", "limit", 20)));
            return new AgentChatResponse(true, appendActionGuide(formatInvoiceMissingOrders(result), proposedAction), model, true, proposedAction, toolCalls, warnings);
        }

        if (isOperationalStatusCountQuestion(message)) {
            Map<String, Object> result = toolService.getOperationalStatusOverview();
            toolCalls.add(Map.of("name", "get_operational_status_overview", "arguments", Map.of()));
            return new AgentChatResponse(true, formatOperationalStatusAnswer(message, result), model, true, proposedAction, toolCalls, warnings);
        }

        ClaimOverviewRequest claimOverviewRequest = parseClaimOverviewRequest(message);
        if (claimOverviewRequest != null) {
            Map<String, Object> result = toolService.getClaimOverview(
                claimOverviewRequest.claimType(),
                claimOverviewRequest.startDate(),
                claimOverviewRequest.endDate(),
                10
            );
            toolCalls.add(Map.of(
                "name", "get_claim_overview",
                "arguments", Map.of(
                    "claimType", claimOverviewRequest.claimType(),
                    "startDate", claimOverviewRequest.startDate().toString(),
                    "endDate", claimOverviewRequest.endDate().toString(),
                    "limit", 10
                )
            ));
            return new AgentChatResponse(true, appendActionGuide(formatClaimOverview(result, claimOverviewRequest.label()), proposedAction), model, true, proposedAction, toolCalls, warnings);
        }

        CancelOverviewRequest cancelOverviewRequest = parseCancelOverviewRequest(message);
        if (cancelOverviewRequest != null) {
            Map<String, Object> result = toolService.searchOrders("", "CANCELLED", 50);
            toolCalls.add(Map.of("name", "search_orders", "arguments", Map.of("keyword", "", "status", "CANCELLED", "limit", 50)));
            return new AgentChatResponse(true, appendActionGuide(formatCancelledOrders(result, cancelOverviewRequest.label()), proposedAction), model, true, proposedAction, toolCalls, warnings);
        }

        DateRangeRequest shipmentRequest = parseShipmentStatsRequest(message);
        if (shipmentRequest != null) {
            Map<String, Object> result = toolService.getShipmentStats(shipmentRequest.startDate(), shipmentRequest.endDate(), 10);
            toolCalls.add(Map.of(
                "name", "get_shipment_stats",
                "arguments", Map.of(
                    "startDate", shipmentRequest.startDate().toString(),
                    "endDate", shipmentRequest.endDate().toString(),
                    "limit", 10
                )
            ));
            return new AgentChatResponse(true, appendActionGuide(formatShipmentStats(result, shipmentRequest.label()), proposedAction), model, true, proposedAction, toolCalls, warnings);
        }

        ProductRankingRequest productRankingRequest = parseProductRankingRequest(message);
        if (productRankingRequest != null) {
            Map<String, Object> result = toolService.getTopProductsByChannel(
                productRankingRequest.startDate(),
                productRankingRequest.endDate(),
                productRankingRequest.channelKeyword(),
                productRankingRequest.limit()
            );
            toolCalls.add(Map.of(
                "name", "get_top_products_by_channel",
                "arguments", Map.of(
                    "startDate", productRankingRequest.startDate().toString(),
                    "endDate", productRankingRequest.endDate().toString(),
                    "channelKeyword", productRankingRequest.channelKeyword(),
                    "limit", productRankingRequest.limit()
                )));
            return new AgentChatResponse(true, formatTopProductsByChannel(result, productRankingRequest), model, true, proposedAction, toolCalls, warnings);
        }

        String printTypeKeyword = extractPrintTypeKeyword(message);
        if (printTypeKeyword != null) {
            Map<String, Object> result = toolService.searchOrdersByPrintType(printTypeKeyword, 20);
            toolCalls.add(Map.of("name", "search_orders_by_print_type", "arguments", Map.of("printTypeKeyword", printTypeKeyword, "limit", 20)));
            return new AgentChatResponse(true, formatPrintTypeOrders(result), model, true, proposedAction, toolCalls, warnings);
        }

        if (containsAny(message, "인쇄구분") && containsAny(message, "일괄 변경", "일괄변경", "전체 변경", "일괄수정")) {
            return new AgentChatResponse(
                true,
                "인쇄구분 일괄 변경은 가능합니다.\n변경 전후 값을 같이 말해 주세요.\n예: `면세점을 별도인쇄로 일괄 변경해줘`",
                model,
                !blank(apiKey),
                proposedAction,
                toolCalls,
                warnings
            );
        }

        String productKeyword = extractProductKeyword(message);
        if (productKeyword != null) {
            Map<String, Object> result = toolService.searchProducts(productKeyword, 10);
            toolCalls.add(Map.of("name", "search_products", "arguments", Map.of("keyword", productKeyword, "limit", 10)));
            return new AgentChatResponse(true, formatProductSearchResult(productKeyword, result), model, true, proposedAction, toolCalls, warnings);
        }

        String orderSearchKeyword = extractOrderSearchKeyword(message);
        if (orderSearchKeyword != null) {
            Map<String, Object> result = toolService.searchOrders(orderSearchKeyword, "ALL", 20);
            toolCalls.add(Map.of("name", "search_orders", "arguments", Map.of("keyword", orderSearchKeyword, "status", "ALL", "limit", 20)));
            return new AgentChatResponse(true, appendActionGuide(formatOrderSearchResult(orderSearchKeyword, result), proposedAction), model, true, proposedAction, toolCalls, warnings);
        }

        if (containsAny(message, "주문 현황", "주문 요약", "주문 상태", "오늘 주문", "일주일", "7일", "한달", "30일", "월간", "주간")) {
            String period = detectOrderOverviewPeriod(message);
            Map<String, Object> result = toolService.getOrderOverview(period);
            toolCalls.add(Map.of("name", "get_order_overview", "arguments", Map.of("period", period)));
            return new AgentChatResponse(true, appendActionGuide(formatOverview(result), proposedAction), model, true, proposedAction, toolCalls, warnings);
        }

        if (containsAny(message, "재고 요약", "재고 현황", "재고 부족")) {
            Map<String, Object> result = toolService.getInventoryOverview();
            toolCalls.add(Map.of("name", "get_inventory_overview", "arguments", Map.of()));
            String answer = containsAny(message, "부족", "위험", "품절")
                ? formatInventoryRisk(result)
                : formatInventory(result);
            return new AgentChatResponse(true, appendActionGuide(answer, proposedAction), model, true, proposedAction, toolCalls, warnings);
        }

        return null;
    }

    private String fallbackAnswer(String message, List<Map<String, Object>> toolCalls) {
        if (!toolCalls.isEmpty()) {
            String toolName = String.valueOf(toolCalls.get(toolCalls.size() - 1).get("name"));
            if ("get_order_overview".equals(toolName)) {
                Map<String, Object> result = toolService.executeTool(toolName, castArgs(toolCalls.get(toolCalls.size() - 1).get("arguments")));
                return formatOverview(result);
            }
            if ("search_orders".equals(toolName)) {
                Map<String, Object> result = toolService.executeTool(toolName, castArgs(toolCalls.get(toolCalls.size() - 1).get("arguments")));
                return formatRecentOrders(result);
            }
            if ("get_shipment_stats".equals(toolName)) {
                Map<String, Object> result = toolService.executeTool(toolName, castArgs(toolCalls.get(toolCalls.size() - 1).get("arguments")));
                return formatShipmentStats(result, "출고일 기준");
            }
            if ("get_claim_overview".equals(toolName)) {
                Map<String, Object> result = toolService.executeTool(toolName, castArgs(toolCalls.get(toolCalls.size() - 1).get("arguments")));
                return formatClaimOverview(result, "반품/교환 기준");
            }
            if ("get_inventory_overview".equals(toolName)) {
                Map<String, Object> result = toolService.executeTool(toolName, castArgs(toolCalls.get(toolCalls.size() - 1).get("arguments")));
                return formatInventory(result);
            }
            if ("search_products".equals(toolName)) {
                Map<String, Object> result = toolService.executeTool(toolName, castArgs(toolCalls.get(toolCalls.size() - 1).get("arguments")));
                return "상품 조회 결과 " + result.getOrDefault("count", 0) + "건입니다.";
            }
            if ("search_orders_by_print_type".equals(toolName)) {
                Map<String, Object> result = toolService.executeTool(toolName, castArgs(toolCalls.get(toolCalls.size() - 1).get("arguments")));
                return formatPrintTypeOrders(result);
            }
        }
        return "현재 질문에 대해 생성된 답변이 없습니다. 더 구체적으로 질문해 주세요.";
    }

    private ClaimOverviewRequest parseClaimOverviewRequest(String message) {
        if (!containsAny(message, "반품", "교환")) {
            return null;
        }
        if (!containsAny(message, "알려", "조회", "현황", "사항", "건수", "통계", "요약")) {
            return null;
        }

        String claimType = message.contains("교환") ? "EXCHANGE" : "RETURN";
        DateRangeRequest range = parseRelativeDateRange(message, claimType.equals("EXCHANGE") ? "교환" : "반품");
        return new ClaimOverviewRequest(claimType, range.startDate(), range.endDate(), range.label());
    }

    private CancelOverviewRequest parseCancelOverviewRequest(String message) {
        if (!message.contains("취소")) {
            return null;
        }
        if (containsAny(message, "발송 취소", "발송취소", "배송 취소")) {
            return null;
        }
        if (!containsAny(message, "알려", "조회", "현황", "사항", "건수", "통계", "요약")) {
            return null;
        }
        DateRangeRequest range = parseRelativeDateRange(message, "취소");
        return new CancelOverviewRequest(range.startDate(), range.endDate(), range.label());
    }

    private DateRangeRequest parseShipmentStatsRequest(String message) {
        if (!containsAny(message, "출고", "발송", "배송완료", "발송완료")) {
            return null;
        }
        if (!containsAny(message, "건수", "몇건", "몇 건", "몇개", "몇 개", "조회", "알려", "현황", "통계")) {
            return null;
        }

        LocalDate today = LocalDate.now(OMS_ZONE);
        Matcher matcher = DATE_PATTERN.matcher(message);
        if (matcher.find()) {
            LocalDate date = LocalDate.of(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3))
            );
            return new DateRangeRequest(date, date, date + " 출고일 기준");
        }

        if (containsAny(message, "그제", "그저께")) {
            LocalDate date = today.minusDays(2);
            return new DateRangeRequest(date, date, date + " 출고일 기준");
        }
        if (message.contains("어제")) {
            LocalDate date = today.minusDays(1);
            return new DateRangeRequest(date, date, date + " 출고일 기준");
        }
        if (message.contains("지난달") || message.contains("전월")) {
            LocalDate start = today.minusMonths(1).withDayOfMonth(1);
            LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
            return new DateRangeRequest(start, end, start.getMonthValue() + "월 출고일 기준");
        }
        if (containsAny(message, "최근 한달", "최근 1개월", "한달", "한 달", "30일", "최근 30일")) {
            return new DateRangeRequest(today.minusDays(29), today, "최근 30일 출고일 기준");
        }
        if (containsAny(message, "이번달", "이번 달", "당월")) {
            LocalDate start = today.withDayOfMonth(1);
            return new DateRangeRequest(start, today, start.getMonthValue() + "월 출고일 기준");
        }
        if (containsAny(message, "일주일", "7일", "최근 7일")) {
            return new DateRangeRequest(today.minusDays(6), today, "최근 7일 출고일 기준");
        }

        return new DateRangeRequest(today, today, today + " 출고일 기준");
    }

    private DateRangeRequest parseRelativeDateRange(String message, String labelPrefix) {
        LocalDate today = LocalDate.now(OMS_ZONE);
        Matcher matcher = DATE_PATTERN.matcher(message);
        if (matcher.find()) {
            LocalDate date = LocalDate.of(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3))
            );
            return new DateRangeRequest(date, date, date + " " + labelPrefix + " 기준");
        }
        if (containsAny(message, "그제", "그저께")) {
            LocalDate date = today.minusDays(2);
            return new DateRangeRequest(date, date, date + " " + labelPrefix + " 기준");
        }
        if (message.contains("어제")) {
            LocalDate date = today.minusDays(1);
            return new DateRangeRequest(date, date, date + " " + labelPrefix + " 기준");
        }
        if (containsAny(message, "지난달", "전월", "저번달", "저번 달")) {
            LocalDate start = today.minusMonths(1).withDayOfMonth(1);
            LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
            return new DateRangeRequest(start, end, start.getMonthValue() + "월 " + labelPrefix + " 기준");
        }
        if (containsAny(message, "최근 한달", "최근 1개월", "한달", "한 달", "30일", "최근 30일")) {
            return new DateRangeRequest(today.minusDays(29), today, "최근 30일 " + labelPrefix + " 기준");
        }
        if (containsAny(message, "이번달", "이번 달", "당월")) {
            LocalDate start = today.withDayOfMonth(1);
            return new DateRangeRequest(start, today, start.getMonthValue() + "월 " + labelPrefix + " 기준");
        }
        if (containsAny(message, "일주일", "7일", "최근 7일")) {
            return new DateRangeRequest(today.minusDays(6), today, "최근 7일 " + labelPrefix + " 기준");
        }
        return new DateRangeRequest(today, today, today + " " + labelPrefix + " 기준");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castArgs(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private boolean containsAny(String source, String... needles) {
        for (String needle : needles) {
            if (source.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String detectOrderOverviewPeriod(String message) {
        if (containsAny(message, "한달", "1개월", "30일", "30 일", "월간", "한 달")) {
            return "30d";
        }
        if (containsAny(message, "일주일", "7일", "7 일", "주간", "이번 주", "최근 일주일")) {
            return "7d";
        }
        return "today";
    }

    private String extractOrderSearchKeyword(String message) {
        String normalized = message.trim();
        if (!containsAny(normalized, "찾아", "조회", "검색", "주문번호", "수취인", "고객")) {
            return null;
        }
        if (containsAny(normalized, "주문 현황", "주문 요약", "주문 상태", "최근 주문", "오늘 주문", "일주일", "7일", "한달", "30일")) {
            return null;
        }
        if (containsAny(normalized, "상품", "제품", "재고")) {
            return null;
        }

        String keyword = normalized
            .replace("주문번호", " ")
            .replace("수취인", " ")
            .replace("고객", " ")
            .replace("주문", " ")
            .replace("찾아줘", " ")
            .replace("찾아 봐", " ")
            .replace("찾아봐", " ")
            .replace("조회해줘", " ")
            .replace("조회", " ")
            .replace("검색해줘", " ")
            .replace("검색", " ")
            .replace("해줘", " ")
            .trim();

        return keyword.isBlank() ? null : keyword;
    }

    private String extractInvoiceOrderKeyword(String message) {
        if (!containsAny(message, "송장번호", "송장 번호", "운송장", "운송장번호", "운송장 번호", "택배번호", "택배 번호")) {
            return null;
        }
        if (!containsAny(message, "주문번호", "주문 번호", "주문")) {
            return null;
        }

        Matcher matcher = ORDER_TOKEN_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group(1).replace("_", "-").trim();
        }

        String keyword = message.trim()
            .replace("송장번호", " ")
            .replace("송장 번호", " ")
            .replace("운송장번호", " ")
            .replace("운송장 번호", " ")
            .replace("택배번호", " ")
            .replace("택배 번호", " ")
            .replace("주문번호", " ")
            .replace("주문 번호", " ")
            .replace("주문", " ")
            .replace("해당하는", " ")
            .replace("알려줘", " ")
            .replace("알려", " ")
            .replace("뭐야", " ")
            .replace("무엇", " ")
            .replace("조회", " ")
            .trim();
        return keyword.isBlank() ? null : keyword;
    }

    private String extractProductKeyword(String message) {
        if (!containsAny(message, "상품", "제품", "sku", "바코드")) {
            return null;
        }
        if (!containsAny(message, "찾아", "조회", "검색")) {
            return null;
        }

        String keyword = message.trim()
            .replace("상품명", " ")
            .replace("상품", " ")
            .replace("제품", " ")
            .replace("sku", " ")
            .replace("바코드", " ")
            .replace("찾아줘", " ")
            .replace("찾아 봐", " ")
            .replace("찾아봐", " ")
            .replace("조회해줘", " ")
            .replace("조회", " ")
            .replace("검색해줘", " ")
            .replace("검색", " ")
            .replace("해줘", " ")
            .trim();

        return keyword.isBlank() ? null : keyword;
    }

    private String extractPrintTypeKeyword(String message) {
        if (!containsAny(message, "인쇄구분", "면세점", "별도인쇄", "일반건")) {
            return null;
        }
        if (!containsAny(message, "보여", "조회", "검색", "주문")) {
            return null;
        }
        String keyword = message.trim()
            .replace("인쇄구분", " ")
            .replace("주문만", " ")
            .replace("주문", " ")
            .replace("목록", " ")
            .replace("보여줘", " ")
            .replace("보여 줘", " ")
            .replace("보여", " ")
            .replace("조회해줘", " ")
            .replace("조회", " ")
            .replace("검색해줘", " ")
            .replace("검색", " ")
            .replace("해줘", " ")
            .trim();
        return keyword.isBlank() ? null : keyword;
    }

    private ProductRankingRequest parseProductRankingRequest(String message) {
        if (!containsAny(message, "상품", "제품")) {
            return null;
        }
        if (!containsAny(message, "많은", "상위", "많이", "top")) {
            return null;
        }
        if (!containsAny(message, "판매처", "네이버", "스마트스토어", "11번가")) {
            return null;
        }

        String channelKeyword = detectChannelKeyword(message);
        if (channelKeyword == null) {
            return null;
        }

        LocalDate now = LocalDate.now(OMS_ZONE);
        LocalDate startDate = now.minusDays(29);
        LocalDate endDate = now;

        if (message.contains("한달") || message.contains("한 달") || message.contains("30일")) {
            startDate = now.minusDays(29);
        } else if (message.contains("일주일") || message.contains("7일") || message.contains("주간")) {
            startDate = now.minusDays(6);
        }

        for (int month = 1; month <= 12; month++) {
            if (message.contains(month + "월")) {
                int year = Year.now().getValue();
                startDate = LocalDate.of(year, month, 1);
                endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
                break;
            }
        }

        int limit = 3;
        for (int top = 10; top >= 1; top--) {
            if (message.contains(top + "개") || message.contains(top + "개만") || message.contains(top + "개 알려")) {
                limit = top;
                break;
            }
        }

        return new ProductRankingRequest(startDate, endDate, channelKeyword, limit);
    }

    private String detectChannelKeyword(String message) {
        if (containsAny(message, "네이버", "스마트스토어")) {
            return "네이버";
        }
        if (message.contains("11번가")) {
            return "11번가";
        }
        return null;
    }

    private String formatOverview(Map<String, Object> result) {
        String periodLabel = switch (String.valueOf(result.getOrDefault("period", ""))) {
            case "30d" -> "최근 30일";
            case "7d" -> "최근 7일";
            default -> "오늘";
        };
        String topChannels = formatTopChannels(result.get("topChannels"));
        String recentDailyCounts = formatRecentDailyCounts(result.get("recentDailyCounts"));
        String latestOrderedAt = formatDateTime(result.get("latestOrderedAt"));

        return """
            %s 주문 현황입니다.

            조회 기간
            - 시작일: %s
            - 종료일: %s

            주문 집계
            - 총 주문: %s건
            - 대기 주문: %s건
            - 확정 주문: %s건
            - 발송 완료: %s건
            - 취소 주문: %s건

            주요 판매처
            %s

            최근 일자별 주문
            %s

            가장 최근 주문
            - 주문번호: %s
            - 주문시각: %s
            - 기준 시간대: %s
            """.formatted(
            periodLabel,
            result.getOrDefault("startDate", "-"),
            result.getOrDefault("endDate", "-"),
            result.getOrDefault("totalOrders", 0),
            result.getOrDefault("pendingOrders", 0),
            result.getOrDefault("confirmedOrders", 0),
            result.getOrDefault("shippedOrders", 0),
            result.getOrDefault("cancelledOrders", 0),
            topChannels,
            recentDailyCounts,
            valueOrDash(result.get("latestOrderNo")),
            latestOrderedAt,
            valueOrDash(result.get("zone"))
        );
    }

    private String formatInventory(Map<String, Object> result) {
        return """
            재고 현황입니다.

            - 전체 상품 수: %s개
            - 전체 재고 수량: %s개
            - 사용 가능 재고: %s개
            - 예약 재고: %s개
            - 품절 상품 수: %s개
            """.formatted(
            result.getOrDefault("totalProducts", 0),
            result.getOrDefault("totalStock", 0),
            result.getOrDefault("availableStock", 0),
            result.getOrDefault("reservedStock", 0),
            result.getOrDefault("outOfStockCount", 0)
        );
    }

    @SuppressWarnings("unchecked")
    private String formatInventoryRisk(Map<String, Object> result) {
        List<Map<String, Object>> products = (List<Map<String, Object>>) result.getOrDefault("riskProducts", List.of());
        if (products.isEmpty()) {
            return "재고 위험 상품이 없습니다.";
        }
        String lines = products.stream()
            .limit(5)
            .map(product -> "- %s: 사용 가능 %s개".formatted(
                valueOrDash(product.get("productName")),
                product.getOrDefault("availableStock", 0)
            ))
            .reduce((a, b) -> a + "\n" + b)
            .orElse("");
        return "재고 부족 위험 상품 상위 5개입니다.\n" + lines;
    }

    private String formatRecentOrders(Map<String, Object> result) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> orders = (List<Map<String, Object>>) result.getOrDefault("orders", List.of());
        if (orders.isEmpty()) {
            return "최근 주문 조회 결과가 없습니다.";
        }
        String lines = orders.stream()
            .sorted(Comparator.comparing(o -> String.valueOf(o.getOrDefault("orderedAt", "")), Comparator.reverseOrder()))
            .limit(5)
            .map(o -> "- 주문번호 %s / 상태 %s / 수취인 %s / 주문시각 %s".formatted(
                valueOrDash(o.get("orderNo")),
                formatOrderStatus(o.get("status")),
                valueOrDash(o.get("recipientName")),
                formatDateTime(o.get("orderedAt"))
            ))
            .reduce((a, b) -> a + "\n" + b)
            .orElse("");
        return "최근 주문 " + result.getOrDefault("count", orders.size()) + "건 중 최신 5건입니다.\n\n" + lines;
    }

    private String formatOrderSearchResult(String keyword, Map<String, Object> result) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> orders = (List<Map<String, Object>>) result.getOrDefault("orders", List.of());

        if (orders.isEmpty()) {
            return """
                '%s' 검색 결과가 없습니다.
                - 검색어를 더 짧게 바꾸거나
                - 주문번호, 수취인명, 고객명 중 하나로 다시 조회해 주세요.
                """.formatted(keyword);
        }

        String lines = orders.stream()
            .sorted(Comparator.comparing(o -> String.valueOf(o.getOrDefault("orderedAt", "")), Comparator.reverseOrder()))
            .limit(3)
            .map(o -> "- %s / %s / %s".formatted(
                valueOrDash(o.get("orderNo")),
                formatOrderStatus(o.get("status")),
                valueOrDash(o.get("recipientName"))
            ))
            .reduce((a, b) -> a + "\n" + b)
            .orElse("");

        return """
            '%s' 검색 결과 %s건입니다.
            %s
            """.formatted(keyword, result.getOrDefault("count", orders.size()), lines);
    }

    private String formatOrderInvoiceResult(String keyword, Map<String, Object> result) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> orders = (List<Map<String, Object>>) result.getOrDefault("orders", List.of());

        if (orders.isEmpty()) {
            return "'%s' 주문을 찾지 못했습니다.\n주문번호를 다시 확인해 주세요.".formatted(keyword);
        }

        Map<String, Object> exact = orders.stream()
            .filter(order -> String.valueOf(order.getOrDefault("orderNo", "")).equalsIgnoreCase(keyword))
            .findFirst()
            .orElse(orders.get(0));

        String orderNo = valueOrDash(exact.get("orderNo"));
        String trackingNo = String.valueOf(exact.getOrDefault("trackingNo", "")).trim();
        if (trackingNo.isBlank()) {
            return "%s 주문에는 아직 송장번호가 없습니다.\n현재 상태는 %s입니다.".formatted(
                orderNo,
                formatOrderStatus(exact.get("status"))
            );
        }

        String carrierName = String.valueOf(exact.getOrDefault("carrierName", "")).trim();
        String carrierText = carrierName.isBlank() ? "" : "\n- 택배사: " + carrierName;
        return "%s 주문의 송장번호는 %s입니다.%s".formatted(orderNo, trackingNo, carrierText);
    }

    @SuppressWarnings("unchecked")
    private String formatProductSearchResult(String keyword, Map<String, Object> result) {
        List<Map<String, Object>> products = (List<Map<String, Object>>) result.getOrDefault("products", List.of());
        if (products.isEmpty()) {
            return """
                '%s' 상품 검색 결과가 없습니다.
                - 상품명, SKU, 바코드를 더 짧게 넣어 다시 조회해 주세요.
                """.formatted(keyword);
        }

        String lines = products.stream()
            .limit(3)
            .map(product -> "- %s / 사용 가능 %s개 / SKU %s".formatted(
                valueOrDash(product.get("productName")),
                product.getOrDefault("availableStock", 0),
                valueOrDash(product.get("sku"))
            ))
            .reduce((a, b) -> a + "\n" + b)
            .orElse("");

        return """
            '%s' 상품 검색 결과 %s건입니다.
            %s
            """.formatted(keyword, result.getOrDefault("count", products.size()), lines);
    }

    @SuppressWarnings("unchecked")
    private String formatInvoiceMissingOrders(Map<String, Object> result) {
        List<Map<String, Object>> orders = (List<Map<String, Object>>) result.getOrDefault("orders", List.of());
        List<Map<String, Object>> missing = orders.stream()
            .filter(order -> !Boolean.TRUE.equals(order.get("invoiceEntered")))
            .limit(5)
            .toList();

        if (missing.isEmpty()) {
            return "최근 확인된 송장 미입력 주문이 없습니다.";
        }

        String lines = missing.stream()
            .map(order -> "- %s / %s / %s".formatted(
                valueOrDash(order.get("orderNo")),
                valueOrDash(order.get("recipientName")),
                formatDateTime(order.get("orderedAt"))
            ))
            .reduce((a, b) -> a + "\n" + b)
            .orElse("");

        return "송장 미입력 주문 상위 %s건입니다.\n%s".formatted(missing.size(), lines);
    }

    private String formatInvoicePendingOverview(Map<String, Object> result) {
        return """
            송장입력대기 주문은 %s건입니다.
            - 기준: 보류 제외, CONFIRMED 상태, 송장번호 미발급
            - 송장 발급완료: %s건
            - 송장출력 대상 전체: %s건
            """.formatted(
            result.getOrDefault("invoicePendingOrders", 0),
            result.getOrDefault("invoiceAssignedOrders", 0),
            result.getOrDefault("totalConfirmedOrders", 0)
        ).trim();
    }

    private String formatOperationalStatusAnswer(String message, Map<String, Object> result) {
        List<String> lines = new ArrayList<>();

        if (containsAny(message, "미매칭", "상품 미매칭", "매칭대기", "매칭 대기")) {
            lines.add("미매칭 상품은 " + result.getOrDefault("unmatchedItems", 0) + "건입니다.");
        }
        if (containsAny(message, "재고할당", "재고 할당", "할당완료", "할당 완료")) {
            lines.add("재고 할당 완료 항목은 " + result.getOrDefault("allocatedItems", 0) + "건입니다.");
            lines.add("- 할당 완료 주문: " + result.getOrDefault("allocatedOrders", 0) + "건");
        }
        if (containsAny(message, "송장입력대기", "송장 입력 대기", "송장대기", "송장 대기", "송장 미발급", "미발급", "송장 미입력")) {
            lines.add("송장입력대기 주문은 " + result.getOrDefault("invoicePendingOrders", 0) + "건입니다.");
        }
        if (containsAny(message, "검수대기", "검수 대기", "발송대기", "발송 대기")) {
            lines.add("검수대기 주문은 " + result.getOrDefault("inspectionWaitingOrders", 0) + "건입니다.");
        }
        if (containsAny(message, "발송완료", "발송 완료", "출고완료", "출고 완료")) {
            lines.add("발송 완료 주문은 " + result.getOrDefault("shippedOrders", 0) + "건입니다.");
        }
        if (containsAny(message, "보류")) {
            lines.add("보류 주문은 " + result.getOrDefault("shippingHoldOrders", 0) + "건입니다.");
        }

        if (lines.isEmpty() || containsAny(message, "작업 현황", "운영 현황", "처리 현황", "전체", "요약")) {
            lines = List.of(
                "현재 운영 상태입니다.",
                "- 미매칭 상품: " + result.getOrDefault("unmatchedItems", 0) + "건",
                "- 재고 할당 완료: " + result.getOrDefault("allocatedItems", 0) + "건",
                "- 송장입력대기: " + result.getOrDefault("invoicePendingOrders", 0) + "건",
                "- 검수대기: " + result.getOrDefault("inspectionWaitingOrders", 0) + "건",
                "- 발송 완료: " + result.getOrDefault("shippedOrders", 0) + "건"
            );
        }

        return String.join("\n", lines);
    }

    @SuppressWarnings("unchecked")
    private String formatShipmentStats(Map<String, Object> result, String label) {
        List<Map<String, Object>> orders = (List<Map<String, Object>>) result.getOrDefault("orders", List.of());
        String period = valueOrDash(result.get("startDate")).equals(valueOrDash(result.get("endDate")))
            ? valueOrDash(result.get("startDate"))
            : valueOrDash(result.get("startDate")) + " ~ " + valueOrDash(result.get("endDate"));

        String sampleLines = orders.stream()
            .limit(3)
            .map(order -> "- %s / %s / %s".formatted(
                valueOrDash(order.get("orderNo")),
                valueOrDash(order.get("recipientName")),
                formatDateTime(order.get("shippedAt"))
            ))
            .reduce((a, b) -> a + "\n" + b)
            .orElse("- 표시할 출고 주문이 없습니다.");

        return """
            %s 출고 완료 건수는 %s건입니다.
            - 조회 기간: %s
            - 상품 수량 합계: %s개
            - 기준: SHIPPED 주문의 수정시각(updatedAt)
            %s
            """.formatted(
            label,
            result.getOrDefault("shippedCount", 0),
            period,
            result.getOrDefault("totalQuantity", 0),
            sampleLines
        );
    }

    @SuppressWarnings("unchecked")
    private String formatClaimOverview(Map<String, Object> result, String label) {
        List<Map<String, Object>> claims = (List<Map<String, Object>>) result.getOrDefault("claims", List.of());
        String period = valueOrDash(result.get("startDate")).equals(valueOrDash(result.get("endDate")))
            ? valueOrDash(result.get("startDate"))
            : valueOrDash(result.get("startDate")) + " ~ " + valueOrDash(result.get("endDate"));

        String sampleLines = claims.stream()
            .limit(3)
            .map(claim -> "- %s / %s / %s".formatted(
                valueOrDash(claim.get("orderNo")),
                formatReturnStatus(claim.get("status")),
                valueOrDash(claim.get("productName"))
            ))
            .reduce((a, b) -> a + "\n" + b)
            .orElse("- 표시할 접수 건이 없습니다.");

        return """
            %s 접수 건수는 %s건입니다.
            - 조회 기간: %s
            - 접수: %s건 / 검수중: %s건
            - 완료: %s건 / 취소: %s건
            %s
            """.formatted(
            label,
            result.getOrDefault("totalClaims", 0),
            period,
            result.getOrDefault("requestedClaims", 0),
            result.getOrDefault("inspectingClaims", 0),
            result.getOrDefault("completedClaims", 0),
            result.getOrDefault("cancelledClaims", 0),
            sampleLines
        );
    }

    @SuppressWarnings("unchecked")
    private String formatCancelledOrders(Map<String, Object> result, String label) {
        List<Map<String, Object>> orders = (List<Map<String, Object>>) result.getOrDefault("orders", List.of());
        if (orders.isEmpty()) {
            return label + " 주문은 없습니다.";
        }

        String lines = orders.stream()
            .sorted(Comparator.comparing(o -> String.valueOf(o.getOrDefault("orderedAt", "")), Comparator.reverseOrder()))
            .limit(5)
            .map(o -> "- %s / %s / %s".formatted(
                valueOrDash(o.get("orderNo")),
                valueOrDash(o.get("recipientName")),
                formatDateTime(o.get("orderedAt"))
            ))
            .reduce((a, b) -> a + "\n" + b)
            .orElse("");

        return """
            %s 주문은 %s건 확인됩니다.
            %s
            """.formatted(label, result.getOrDefault("count", orders.size()), lines);
    }

    @SuppressWarnings("unchecked")
    private String formatTopProductsByChannel(Map<String, Object> result, ProductRankingRequest request) {
        List<Map<String, Object>> products = (List<Map<String, Object>>) result.getOrDefault("products", List.of());
        if (products.isEmpty()) {
            return """
                %s 판매처에서 %s 기간에 집계된 상품 데이터가 없습니다.
                - 판매처명이나 기간을 바꿔 다시 조회해 주세요.
                """.formatted(channelDisplayName(request.channelKeyword()), periodLabel(request.startDate(), request.endDate()));
        }

        String lines = products.stream()
            .limit(request.limit())
            .map((product) -> "- %s: 주문수량 %s개, 주문건수 %s건".formatted(
                valueOrDash(product.get("productName")),
                product.getOrDefault("quantity", 0),
                product.getOrDefault("orderCount", 0)
            ))
            .reduce((a, b) -> a + "\n" + b)
            .orElse("");

        return """
            %s 판매처에서 %s 기간 주문량이 많은 상품 상위 %s개입니다.
            %s
            """.formatted(
            channelDisplayName(request.channelKeyword()),
            periodLabel(request.startDate(), request.endDate()),
            request.limit(),
            lines
        );
    }

    @SuppressWarnings("unchecked")
    private String formatTopChannels(Object rawValue) {
        if (!(rawValue instanceof List<?> channels) || channels.isEmpty()) {
            return "- 집계된 판매처 정보가 없습니다.";
        }
        return channels.stream()
            .filter(Map.class::isInstance)
            .map(Map.class::cast)
            .limit(5)
            .map(channel -> "- %s: %s건".formatted(
                valueOrDash(channel.get("channelName")),
                channel.getOrDefault("count", 0)
            ))
            .reduce((a, b) -> a + "\n" + b)
            .orElse("- 집계된 판매처 정보가 없습니다.");
    }

    @SuppressWarnings("unchecked")
    private String formatRecentDailyCounts(Object rawValue) {
        if (!(rawValue instanceof List<?> dailyCounts) || dailyCounts.isEmpty()) {
            return "- 최근 일자별 주문 데이터가 없습니다.";
        }
        return dailyCounts.stream()
            .filter(Map.class::isInstance)
            .map(Map.class::cast)
            .limit(7)
            .map(day -> "- %s: %s건".formatted(
                valueOrDash(day.get("date")),
                day.getOrDefault("count", 0)
            ))
            .reduce((a, b) -> a + "\n" + b)
            .orElse("- 최근 일자별 주문 데이터가 없습니다.");
    }

    private String formatDateTime(Object rawValue) {
        String value = rawValue != null ? String.valueOf(rawValue).trim() : "";
        if (value.isBlank()) {
            return "-";
        }
        try {
            return LocalDateTime.parse(value).format(DATE_TIME_FORMAT);
        } catch (Exception ignored) {
        }
        try {
            return LocalDate.parse(value).toString();
        } catch (Exception ignored) {
        }
        return value;
    }

    private String formatOrderStatus(Object rawValue) {
        String value = rawValue != null ? String.valueOf(rawValue).trim().toUpperCase(Locale.ROOT) : "";
        return switch (value) {
            case "PENDING" -> "대기";
            case "CONFIRMED" -> "확정";
            case "SHIPPED" -> "발송 완료";
            case "CANCELLED" -> "취소";
            default -> value.isBlank() ? "-" : value;
        };
    }

    private String formatReturnStatus(Object rawValue) {
        String value = rawValue != null ? String.valueOf(rawValue).trim().toUpperCase(Locale.ROOT) : "";
        return switch (value) {
            case "REQUESTED" -> "접수";
            case "INSPECTING" -> "검수중";
            case "COMPLETED" -> "완료";
            case "CANCELLED" -> "취소";
            default -> value.isBlank() ? "-" : value;
        };
    }

    private String formatPrintTypeOrders(Map<String, Object> result) {
        if (!Boolean.TRUE.equals(result.get("matched"))) {
            return "해당 인쇄구분을 찾지 못했습니다.\n등록된 인쇄구분명이나 코드를 같이 적어 주세요.";
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> orders = (List<Map<String, Object>>) result.get("orders");
        String printTypeName = valueOrDash(result.get("printTypeName"));
        int count = orders != null ? orders.size() : 0;
        if (count == 0) {
            return printTypeName + " 인쇄구분 주문은 없습니다.";
        }
        String lines = orders.stream()
            .limit(5)
            .map(order -> "- %s / %s / %s".formatted(
                valueOrDash(order.get("orderNo")),
                formatOrderStatus(order.get("status")),
                valueOrDash(order.get("recipientName"))
            ))
            .reduce((a, b) -> a + "\n" + b)
            .orElse("- 표시할 주문이 없습니다.");
        return """
            %s 인쇄구분 주문 %d건입니다.
            %s
            """.formatted(printTypeName, count, lines).trim();
    }

    private boolean isGreetingMessage(String message) {
        String normalized = message != null ? message.trim().toLowerCase(Locale.ROOT) : "";
        return List.of("안녕", "안녕하세요", "하이", "hello", "hi", "반가워").contains(normalized);
    }

    private String buildGreetingReply(String rawMessage) {
        return """
            안녕하세요.
            주문, 재고, 반품, 출고 현황 조회와 승인형 작업 제안을 처리할 수 있습니다.
            예: `어제 출고 건수 알려줘`, `사방넷 주문수집 실행해줘`
            """.trim();
    }

    private String buildCapabilityReply() {
        return """
            지금 가능한 작업입니다.
            - 조회: 주문, 출고, 반품/교환, 재고, 상품, 인쇄구분 주문 조회
            - 승인형 실행: 사방넷 주문수집, 전체 송장 자동부여, 어제 미출고 일괄 보류, 인쇄구분 일괄 변경
            - 예: `면세점 주문 보여줘`, `면세점을 별도인쇄로 일괄 변경해줘`
            """.trim();
    }

    private String valueOrDash(Object rawValue) {
        String value = rawValue != null ? String.valueOf(rawValue).trim() : "";
        return value.isBlank() ? "-" : value;
    }

    private String appendActionGuide(String answer, AgentActionProposal proposedAction) {
        if (proposedAction == null) {
            return answer;
        }
        return answer + "\n\n실행 준비\n- 요청한 작업: " + proposedAction.title() + "\n- 아래 승인 버튼을 누르면 실제 처리됩니다.";
    }

    private String formatActionProposalMessage(AgentActionProposal proposedAction) {
        return """
            요청한 작업을 확인했습니다.
            - 작업: %s
            - 안내: 아래 승인 버튼을 누르면 실제 처리됩니다.
            """.formatted(proposedAction.title());
    }

    private String channelDisplayName(String channelKeyword) {
        if ("네이버".equals(channelKeyword)) {
            return "네이버";
        }
        if ("11번가".equals(channelKeyword)) {
            return "11번가";
        }
        return channelKeyword;
    }

    private String periodLabel(LocalDate startDate, LocalDate endDate) {
        if (startDate.getDayOfMonth() == 1 && endDate.getDayOfMonth() == endDate.lengthOfMonth() && startDate.getMonth() == endDate.getMonth()) {
            return startDate.getMonthValue() + "월";
        }
        return startDate + " ~ " + endDate;
    }

    private record ProductRankingRequest(
        LocalDate startDate,
        LocalDate endDate,
        String channelKeyword,
        int limit
    ) {}

    private record ClaimOverviewRequest(
        String claimType,
        LocalDate startDate,
        LocalDate endDate,
        String label
    ) {}

    private record CancelOverviewRequest(
        LocalDate startDate,
        LocalDate endDate,
        String label
    ) {}

    private record DateRangeRequest(
        LocalDate startDate,
        LocalDate endDate,
        String label
    ) {}
}
