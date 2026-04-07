package com.oms.collector.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oms.collector.agent.dto.AgentChatMessage;
import com.oms.collector.agent.dto.AgentChatRequest;
import com.oms.collector.agent.dto.AgentChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OmsAgentService {

    private static final String RESPONSES_API = "https://api.openai.com/v1/responses";
    private static final String SYSTEM_PROMPT = """
        너는 OMS 운영 도우미다.
        목표는 한국어로 간결하고 실무적으로 답하는 것이다.
        반드시 현재 OMS 도구로 확인한 사실만 단정적으로 말해라.
        데이터가 부족하면 추정이라고 명시해라.
        허용 범위는 조회, 요약, 분석, 우선순위 제안이다.
        삭제, 수정, 발송, 재고조정 같은 실행형 작업은 하지 말고 실행하지 않았다고 명확히 적어라.
        답변 형식:
        - 첫 문단에 핵심 결론
        - 필요하면 짧은 항목으로 근거
        - 숫자와 상태명은 원문 그대로 유지
        """;

    private final OmsAgentToolService toolService;
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
            return new AgentChatResponse(false, "AI 에이전트가 비활성화되어 있습니다. `openai.agent.enabled=true`로 설정하세요.", model, false, toolCalls, warnings);
        }

        if (request == null || blank(request.message())) {
            return new AgentChatResponse(false, "질문이 비어 있습니다.", model, !blank(apiKey), toolCalls, warnings);
        }

        if (blank(apiKey)) {
            warnings.add("OPENAI_API_KEY가 설정되지 않아 AI 응답 대신 연결 안내만 제공합니다.");
            return new AgentChatResponse(
                false,
                "AI 에이전트 연결 전입니다. 서버 환경변수 `OPENAI_API_KEY`를 설정하면 주문/재고 요약형 질의를 처리할 수 있습니다.",
                model,
                false,
                toolCalls,
                warnings
            );
        }

        WebClient client = WebClient.builder()
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();

        try {
            JsonNode response = createResponse(client, buildInitialPayload(request));
            for (int i = 0; i < 6 && hasFunctionCalls(response); i++) {
                ArrayNode toolOutputs = objectMapper.createArrayNode();
                for (JsonNode node : response.path("output")) {
                    if (!"function_call".equals(node.path("type").asText())) {
                        continue;
                    }
                    String name = node.path("name").asText();
                    String callId = node.path("call_id").asText();
                    JsonNode args = parseJson(node.path("arguments").asText("{}"));
                    Map<String, Object> toolResult = executeTool(name, args);
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
                answer = "현재 질문에 대해 생성된 답변이 없습니다. 더 구체적으로 질문해 주세요.";
            }

            return new AgentChatResponse(true, answer, model, true, toolCalls, warnings);
        } catch (WebClientResponseException e) {
            log.error("OpenAI API error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            warnings.add("OpenAI API 호출이 실패했습니다.");
            return new AgentChatResponse(false, "AI 호출에 실패했습니다. API 키, 모델명, 네트워크 설정을 확인하세요.", model, true, toolCalls, warnings);
        } catch (Exception e) {
            log.error("Agent chat failed", e);
            warnings.add("에이전트 처리 중 예외가 발생했습니다.");
            return new AgentChatResponse(false, "에이전트 처리 중 오류가 발생했습니다. 서버 로그를 확인하세요.", model, true, toolCalls, warnings);
        }
    }

    private JsonNode createResponse(WebClient client, ObjectNode payload) {
        return client.post()
            .uri(RESPONSES_API)
            .bodyValue(payload)
            .retrieve()
            .bodyToMono(JsonNode.class)
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

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
