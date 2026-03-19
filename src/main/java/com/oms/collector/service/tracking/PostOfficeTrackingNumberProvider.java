package com.oms.collector.service.tracking;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 우체국 택배 OpenAPI 송장번호 발급 구현체
 *
 * ──────────────────────────────────────────────────────────────
 * 활성화 방법 (application.yml):
 *   tracking:
 *     provider: post-office
 *     post-office:
 *       api-url: https://biz.epost.go.kr/...   # 우체국 API URL
 *       api-key: YOUR_API_KEY
 *       customer-id: YOUR_CUSTOMER_ID
 * ──────────────────────────────────────────────────────────────
 *
 * 우체국 API 문서 참고:
 *   https://biz.epost.go.kr/customir/guide/guide.jsp
 *
 * TODO: 실제 API 연동 시 아래 issueFromApi() 구현 후
 *       issue() 메서드에서 issueFromApi() 호출로 교체
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "tracking.provider", havingValue = "post-office")
public class PostOfficeTrackingNumberProvider implements TrackingNumberProvider {

    @Value("${tracking.post-office.api-url:}")
    private String apiUrl;

    @Value("${tracking.post-office.api-key:}")
    private String apiKey;

    @Value("${tracking.post-office.customer-id:}")
    private String customerId;

    @Override
    public boolean supports(String carrierCode) {
        // 우체국 전용 — 다른 택배사는 지원 안 함
        return "POST".equals(carrierCode);
    }

    @Override
    public String issue(String carrierCode, String carrierName, String orderNo) {
        if (!"POST".equals(carrierCode)) {
            throw new UnsupportedOperationException(
                "PostOfficeTrackingNumberProvider는 우체국(POST)만 지원합니다. 요청 택배사: " + carrierCode
            );
        }
        log.info("[우체국 API] 송장번호 발급 요청: orderNo={}", orderNo);
        return issueFromApi(orderNo);
    }

    /**
     * ──────────────────────────────────────────────
     * TODO: 우체국 OpenAPI 실제 구현
     * ──────────────────────────────────────────────
     * 구현 예시:
     *
     * private String issueFromApi(String orderNo) {
     *     HttpClient client = HttpClient.newHttpClient();
     *     String requestBody = buildRequestXml(orderNo);   // 우체국 API 포맷
     *     HttpRequest request = HttpRequest.newBuilder()
     *         .uri(URI.create(apiUrl))
     *         .header("Content-Type", "application/xml")
     *         .header("Authorization", "Bearer " + apiKey)
     *         .POST(HttpRequest.BodyPublishers.ofString(requestBody))
     *         .build();
     *     HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
     *     return parseTrackingNo(response.body());         // 응답에서 송장번호 파싱
     * }
     */
    private String issueFromApi(String orderNo) {
        // 실제 API 구현 전 임시 예외
        throw new UnsupportedOperationException(
            "우체국 API 미구현. application.yml 의 api-url / api-key / customer-id 설정 후 구현 필요.\n" +
            "임시 사용: tracking.provider=mock 으로 변경"
        );
    }
}
