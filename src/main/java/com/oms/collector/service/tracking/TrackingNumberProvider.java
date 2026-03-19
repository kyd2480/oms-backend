package com.oms.collector.service.tracking;

/**
 * 송장번호 발급 인터페이스
 *
 * 현재: MockTrackingNumberProvider (순번 기반 임시 발급)
 * 교체: PostOfficeTrackingNumberProvider (우체국 OpenAPI 연동)
 *
 * 교체 방법:
 *   1. PostOfficeTrackingNumberProvider 에 @Primary 추가 (또는 Mock에서 제거)
 *   2. application.yml 에 우체국 API 키/URL 설정
 *   3. 빌드 후 배포 — InvoiceController 코드 변경 불필요
 */
public interface TrackingNumberProvider {

    /**
     * 송장번호 단건 발급
     *
     * @param carrierCode 택배사 코드 (CJ / POST / HANJIN / LOTTE / LOGEN / DIRECT)
     * @param carrierName 택배사명
     * @param orderNo     주문번호 (API 요청 시 참조용)
     * @return 발급된 송장번호
     */
    String issue(String carrierCode, String carrierName, String orderNo);

    /**
     * 해당 택배사를 지원하는지 여부
     * - false 반환 시 Mock 폴백 또는 예외 처리
     */
    default boolean supports(String carrierCode) {
        return true;
    }
}
