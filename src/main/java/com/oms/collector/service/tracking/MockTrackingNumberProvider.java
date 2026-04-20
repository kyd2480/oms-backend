package com.oms.collector.service.tracking;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 임시 송장번호 발급 구현체
 *
 * ⚠️ 실제 택배사 API 연동 전까지만 사용
 *
 * 활성화 조건: application.yml
 *   tracking:
 *     provider: mock   ← 이 값일 때 활성화 (기본값)
 *
 * 우체국 API 연동 시:
 *   tracking:
 *     provider: post-office  ← 변경만 하면 자동으로 PostOfficeTrackingNumberProvider 로 전환
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "tracking.provider", havingValue = "mock", matchIfMissing = true)
public class MockTrackingNumberProvider implements TrackingNumberProvider {

    // AtomicLong: 동시 호출 시에도 중복 없는 유니크 순번 보장
    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis());

    @Override
    public IssueResult issue(String carrierCode, String carrierName, String orderNo) {
        String prefix = switch (carrierCode != null ? carrierCode : "") {
            case "CJ"     -> "6";
            case "POST"   -> "6";
            case "HANJIN" -> "7";
            case "LOTTE"  -> "8";
            case "LOGEN"  -> "9";
            default       -> "6";
        };
        long seq = SEQ.incrementAndGet() % 1_000_000_000_000L;
        String trackingNo = prefix + String.format("%012d", seq);
        log.info("[Mock] 송장번호 발급: {} → {} ({})", orderNo, trackingNo, carrierName);
        return new IssueResult(trackingNo, null);
    }

    @Override
    public void cancel(String carrierCode, String carrierName, String orderNo,
                       String trackingNo, String reservationNo) {
        log.info("[Mock] 송장번호 취소: {} → {} ({})", orderNo, trackingNo, carrierName);
    }
}
