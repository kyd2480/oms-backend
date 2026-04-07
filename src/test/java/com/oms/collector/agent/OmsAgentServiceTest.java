package com.oms.collector.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.collector.agent.dto.AgentActionProposal;
import com.oms.collector.agent.dto.AgentChatRequest;
import com.oms.collector.agent.dto.AgentChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Year;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OmsAgentServiceTest {

    @Mock
    private OmsAgentToolService toolService;

    @Mock
    private AgentActionService agentActionService;

    private OmsAgentService service;

    @BeforeEach
    void setUp() {
        service = new OmsAgentService(toolService, agentActionService, new ObjectMapper());
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "model", "gpt-5-mini");
        ReflectionTestUtils.setField(service, "agentEnabled", true);

        lenient().when(agentActionService.propose(anyString(), anyString())).thenReturn(null);
    }

    @Test
    @DisplayName("최근 7일 주문 현황 질문은 7일 케이스로 응답한다")
    void weeklyOrderOverviewCase() {
        when(toolService.getOrderOverview("7d")).thenReturn(orderOverview("7d", 150));

        AgentChatResponse response = service.chat(request("일주일치 주문 현황 요약해줘"));

        assertThat(response.success()).isTrue();
        assertThat(response.answer()).contains("최근 7일 주문 현황");
        assertThat(response.toolCalls()).hasSize(1);
        assertThat(response.toolCalls().get(0).get("name")).isEqualTo("get_order_overview");
        assertThat(((Map<?, ?>) response.toolCalls().get(0).get("arguments")).get("period")).isEqualTo("7d");
    }

    @Test
    @DisplayName("월간 주문 현황 질문은 30일 케이스로 응답한다")
    void monthlyOrderOverviewCase() {
        when(toolService.getOrderOverview("30d")).thenReturn(orderOverview("30d", 9597));

        AgentChatResponse response = service.chat(request("한달치 주문 현황 요약해줘"));

        assertThat(response.answer()).contains("최근 30일 주문 현황");
        assertThat(((Map<?, ?>) response.toolCalls().get(0).get("arguments")).get("period")).isEqualTo("30d");
    }

    @Test
    @DisplayName("주문 검색 결과 없음은 짧고 깔끔하게 응답한다")
    void orderSearchNoResultCase() {
        when(toolService.searchOrders("김영덕", "ALL", 20)).thenReturn(Map.of(
            "count", 0,
            "orders", List.of()
        ));

        AgentChatResponse response = service.chat(request("김영덕 찾아줘"));

        assertThat(response.answer()).contains("'김영덕' 검색 결과가 없습니다.");
        assertThat(response.answer()).doesNotContain("keyword").doesNotContain("status").doesNotContain("limit");
        assertThat(response.toolCalls().get(0).get("name")).isEqualTo("search_orders");
    }

    @Test
    @DisplayName("판매처 인기 상품 질문은 상품 순위 케이스로 응답한다")
    void topProductsByChannelCase() {
        int year = Year.now().getValue();
        when(toolService.getTopProductsByChannel(any(), any(), anyString(), anyInt())).thenReturn(Map.of(
            "products", List.of(
                Map.of("productName", "아이보리 세트", "quantity", 120, "orderCount", 52),
                Map.of("productName", "블랙 세트", "quantity", 98, "orderCount", 41),
                Map.of("productName", "그레이 세트", "quantity", 76, "orderCount", 33)
            )
        ));

        AgentChatResponse response = service.chat(request("3월 한달간 네이버 판매처의 주문량 많은 상품 3개 알려줘"));

        assertThat(response.answer()).contains("네이버 판매처에서 3월 기간 주문량이 많은 상품 상위 3개");
        assertThat(response.answer()).contains("아이보리 세트").contains("주문수량 120개");
        assertThat(response.toolCalls().get(0).get("name")).isEqualTo("get_top_products_by_channel");
        Map<?, ?> arguments = (Map<?, ?>) response.toolCalls().get(0).get("arguments");
        assertThat(arguments.get("startDate")).isEqualTo(year + "-03-01");
        assertThat(arguments.get("channelKeyword")).isEqualTo("네이버");
    }

    @Test
    @DisplayName("재고 부족 질문은 위험 상품 목록으로 응답한다")
    void inventoryRiskCase() {
        when(toolService.getInventoryOverview()).thenReturn(Map.of(
            "riskProducts", List.of(
                Map.of("productName", "행거 커버", "availableStock", 2),
                Map.of("productName", "압축팩", "availableStock", 4)
            )
        ));

        AgentChatResponse response = service.chat(request("재고 부족 상품 알려줘"));

        assertThat(response.answer()).contains("재고 부족 위험 상품 상위 5개");
        assertThat(response.answer()).contains("행거 커버: 사용 가능 2개");
    }

    @Test
    @DisplayName("상품 검색 질문은 상품 케이스로 응답한다")
    void productSearchCase() {
        when(toolService.searchProducts(anyString(), anyInt())).thenReturn(Map.of(
            "count", 2,
            "products", List.of(
                Map.of("productName", "XF 매트", "availableStock", 15, "sku", "XF-01"),
                Map.of("productName", "XF 커버", "availableStock", 9, "sku", "XF-02")
            )
        ));

        AgentChatResponse response = service.chat(request("상품 XF 검색해줘"));

        assertThat(response.answer()).contains("'xf' 상품 검색 결과 2건");
        assertThat(response.answer()).contains("XF 매트 / 사용 가능 15개 / SKU XF-01");
        assertThat(response.toolCalls().get(0).get("name")).isEqualTo("search_products");
        assertThat(((Map<?, ?>) response.toolCalls().get(0).get("arguments")).get("keyword")).isEqualTo("xf");
    }

    @Test
    @DisplayName("송장 미입력 질문은 누락 주문 목록으로 응답한다")
    void invoiceMissingCase() {
        when(toolService.searchOrders("", "CONFIRMED", 20)).thenReturn(Map.of(
            "orders", List.of(
                Map.of("orderNo", "OMS-1", "recipientName", "박서준", "orderedAt", "2026-03-30T11:35:42", "invoiceEntered", false),
                Map.of("orderNo", "OMS-2", "recipientName", "김민지", "orderedAt", "2026-03-30T09:12:10", "invoiceEntered", false)
            )
        ));

        AgentChatResponse response = service.chat(request("송장 미입력 주문 보여줘"));

        assertThat(response.answer()).contains("송장 미입력 주문 상위 2건");
        assertThat(response.answer()).contains("OMS-1 / 박서준");
        assertThat(response.toolCalls().get(0).get("name")).isEqualTo("search_orders");
    }

    @Test
    @DisplayName("실행 요청은 조회 없이도 실행 제안을 응답에 포함한다")
    void directQueryIncludesActionProposal() {
        when(agentActionService.propose(anyString(), anyString())).thenReturn(
            new AgentActionProposal("AUTO_ASSIGN_INVOICE", "송장 자동부여 실행", "설명", "token-1", true, "medium", Map.of("orderNo", "OMS-1"))
        );

        AgentChatResponse response = service.chat(request("OMS-1 송장번호 부여해줘"));

        assertThat(response.proposedAction()).isNotNull();
        assertThat(response.proposedAction().actionType()).isEqualTo("AUTO_ASSIGN_INVOICE");
        assertThat(response.toolCalls()).isEmpty();
    }

    @Test
    @DisplayName("실행형 요청은 조회 없이 바로 승인 버튼용 제안으로 응답한다")
    void actionRequestReturnsProposalImmediately() {
        when(agentActionService.propose(anyString(), anyString())).thenReturn(
            new AgentActionProposal("CANCEL_SHIPMENT", "발송 취소 실행", "설명", "token-2", true, "high", Map.of("orderNo", "OMS-9"))
        );

        AgentChatResponse response = service.chat(request("OMS-9 발송 취소해줘"));

        assertThat(response.proposedAction()).isNotNull();
        assertThat(response.proposedAction().actionType()).isEqualTo("CANCEL_SHIPMENT");
        assertThat(response.toolCalls()).isEmpty();
        assertThat(response.answer()).contains("아래 승인 버튼");
    }

    private AgentChatRequest request(String message) {
        return new AgentChatRequest(message, "관리자", List.of());
    }

    private Map<String, Object> orderOverview(String period, int totalOrders) {
        return Map.ofEntries(
            Map.entry("period", period),
            Map.entry("startDate", "2026-04-01"),
            Map.entry("endDate", "2026-04-07"),
            Map.entry("totalOrders", totalOrders),
            Map.entry("pendingOrders", 12),
            Map.entry("confirmedOrders", 5),
            Map.entry("shippedOrders", 3),
            Map.entry("cancelledOrders", 1),
            Map.entry("topChannels", List.of(Map.of("channelName", "네이버", "count", 80))),
            Map.entry("recentDailyCounts", List.of(Map.of("date", "2026-04-07", "count", 20))),
            Map.entry("latestOrderNo", "OMS-20260407-0001"),
            Map.entry("latestOrderedAt", "2026-04-07T10:00:00"),
            Map.entry("zone", "Asia/Seoul")
        );
    }
}
