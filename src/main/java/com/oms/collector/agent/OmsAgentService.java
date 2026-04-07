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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OmsAgentService {

    private static final String RESPONSES_API = "https://api.openai.com/v1/responses";
    private static final Duration OPENAI_TIMEOUT = Duration.ofSeconds(45);
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String SYSTEM_PROMPT = """
        너는 OMS 운영 도우미다.
        목표는 한국어로 짧고 실무적으로 답하는 것이다.
        반드시 현재 OMS 도구로 확인한 사실만 단정적으로 말해라.
        데이터가 부족하면 추정이라고 명시해라.
        허용 범위는 조회, 요약, 분석, 우선순위 제안이다.
        실행형 작업 요청이 와도 직접 실행하지 말고, 어떤 작업이 가능한지와 확인이 필요하다는 점만 설명해라.
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

    public AgentChatResponse chat(AgentChatRequest request) {
        List<String> warnings = new ArrayList<>();
        List<Map<String, Object>> toolCalls = new ArrayList<>();

        if (!agentEnabled) {
            return new AgentChatResponse(false, "AI 에이전트가 비활성화되어 있습니다. `openai.agent.enabled=true`로 설정하세요.", model, false, null, toolCalls, warnings);
        }

        if (request == null || blank(request.message())) {
            return new AgentChatResponse(false, "질문이 비어 있습니다.", model, !blank(apiKey), null, toolCalls, warnings);
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

        AgentActionProposal proposedAction = agentActionService.propose(request.message(), request.userName());

        AgentChatResponse direct = handleDirectQuery(request, warnings, proposedAction);
        if (direct != null) {
            return direct;
        }

        HttpClient httpClient = HttpClient.create()
            .responseTimeout(OPENAI_TIMEOUT);

        WebClient client = WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();

        try {
            log.info("Agent request started: user={}, model={}", request.userName(), model);
            JsonNode response = createResponse(client, buildInitialPayload(request));
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
                response = createResponse(client, nextPayload);
            }

            String answer = extractText(response);
            if (blank(answer)) {
                warnings.add("모델 응답이 비어 있어 기본 메시지로 대체했습니다.");
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
            return new AgentChatResponse(false, "에이전트 처리 중 오류가 발생했습니다: " + e.getMessage(), model, true, null, toolCalls, warnings);
        }
    }

    private JsonNode createResponse(WebClient client, ObjectNode payload) {
        return client.post()
            .uri(RESPONSES_API)
            .bodyValue(payload)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .timeout(OPENAI_TIMEOUT)
            .block();
    }

    private ObjectNode buildInitialPayload(AgentChatRequest request) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", model);
        payload.put("instructions", SYSTEM_PROMPT);
        payload.put("max_output_tokens", 900);
        payload.set("input", objectMapper.getNodeFactory().textNode(buildTranscript(request)));
        payload.set("tools", buildTools());
        return payload;
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
            "주문번호, 수취인, 고객명, 판매처명으로 주문을 조회한다. status는 ALL 또는 PENDING, CONFIRMED, SHIPPED, CANCELLED를 사용한다.",
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
            case "get_inventory_overview" -> toolService.getInventoryOverview();
            case "search_products" -> toolService.searchProducts(
                args.path("keyword").asText(""),
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

    private AgentChatResponse handleDirectQuery(AgentChatRequest request, List<String> warnings, AgentActionProposal proposedAction) {
        String message = request.message() != null ? request.message().toLowerCase(Locale.ROOT) : "";
        List<Map<String, Object>> toolCalls = new ArrayList<>();

        if (containsAny(message, "최근 주문", "최근 주문건", "최근 주문 보여", "latest orders")) {
            Map<String, Object> result = toolService.searchOrders("", "ALL", 10);
            toolCalls.add(Map.of("name", "search_orders", "arguments", Map.of("keyword", "", "status", "ALL", "limit", 10)));
            return new AgentChatResponse(true, appendActionGuide(formatRecentOrders(result), proposedAction), model, true, proposedAction, toolCalls, warnings);
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
            return new AgentChatResponse(true, appendActionGuide(formatInventory(result), proposedAction), model, true, proposedAction, toolCalls, warnings);
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
            if ("get_inventory_overview".equals(toolName)) {
                Map<String, Object> result = toolService.executeTool(toolName, castArgs(toolCalls.get(toolCalls.size() - 1).get("arguments")));
                return formatInventory(result);
            }
            if ("search_products".equals(toolName)) {
                Map<String, Object> result = toolService.executeTool(toolName, castArgs(toolCalls.get(toolCalls.size() - 1).get("arguments")));
                return "상품 조회 결과 " + result.getOrDefault("count", 0) + "건입니다.";
            }
        }
        return "현재 질문에 대해 생성된 답변이 없습니다. 더 구체적으로 질문해 주세요.";
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
}
