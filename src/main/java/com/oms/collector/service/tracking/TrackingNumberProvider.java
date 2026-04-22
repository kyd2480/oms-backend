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
     * 송장번호 발급 결과
     * trackingNo    : 등기번호 (regiNo, 13자리)
     * poReqNo       : 우체국 소포신청번호 (reqNo, 18자리, 건당 부여) — 취소 필수
     * reservationNo : 우체국 예약번호 (resNo, 16자리, 일자당 부여) — 취소 필수
     * reqYmd        : 신청일자 yyyyMMdd
     */
    record IssueResult(
        String trackingNo,
        String poReqNo,
        String reservationNo,
        String reqYmd,
        String apiProvider,
        String apiAction,
        String responseCode,
        String responseMessage,
        String rawResponse
    ) {
        public IssueResult(String trackingNo, String poReqNo, String reservationNo, String reqYmd) {
            this(trackingNo, poReqNo, reservationNo, reqYmd, null, null, null, null, null);
        }
    }

    record CancelResult(
        boolean success,
        String apiProvider,
        String apiAction,
        String responseCode,
        String responseMessage,
        String rawResponse
    ) {
        public static CancelResult ok() {
            return new CancelResult(true, null, null, null, null, null);
        }
    }

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
     * @param reservationNo  예약번호 (resNo)
     * @param reqYmd         신청일자 yyyyMMdd
     */
    default void cancel(String carrierCode, String carrierName, String poReqNo,
                        String trackingNo, String reservationNo, String reqYmd) {
        if (!supports(carrierCode)) {
            throw new UnsupportedOperationException("해당 택배사 송장취소를 지원하지 않습니다: " + carrierCode);
        }
    }

    default CancelResult cancelWithResult(String carrierCode, String carrierName, String poReqNo,
                                          String trackingNo, String reservationNo, String reqYmd) {
        cancel(carrierCode, carrierName, poReqNo, trackingNo, reservationNo, reqYmd);
        return CancelResult.ok();
    }

    default boolean supports(String carrierCode) {
        return true;
    }
}
