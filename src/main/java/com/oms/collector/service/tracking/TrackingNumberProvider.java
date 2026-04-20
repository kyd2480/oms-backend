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

    /** 송장번호 발급 결과 — trackingNo(등기번호) + reservationNo(우체국 예약번호, 취소 시 필요) */
    record IssueResult(String trackingNo, String reservationNo) {}

    /**
     * 송장번호 단건 발급
     *
     * @param carrierCode 택배사 코드 (CJ / POST / HANJIN / LOTTE / LOGEN / DIRECT)
     * @param carrierName 택배사명
     * @param orderNo     주문번호
     * @return IssueResult(trackingNo, reservationNo)
     */
    IssueResult issue(String carrierCode, String carrierName, String orderNo);

    /**
     * 송장번호 취소
     *
     * @param carrierCode    택배사 코드
     * @param carrierName    택배사명
     * @param orderNo        주문번호
     * @param trackingNo     등기번호 (regiNo)
     * @param reservationNo  예약번호 (resNo, 우체국 cancel 필수)
     */
    default void cancel(String carrierCode, String carrierName, String orderNo,
                        String trackingNo, String reservationNo) {
        if (!supports(carrierCode)) {
            throw new UnsupportedOperationException("해당 택배사 송장취소를 지원하지 않습니다: " + carrierCode);
        }
    }

    default boolean supports(String carrierCode) {
        return true;
    }
}
