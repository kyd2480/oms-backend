package com.oms.collector.service;

import com.oms.collector.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * OMS 주문번호 생성 서비스
 * 
 * 형식: OMS-YYYYMMDD-XXXX
 * 예시: OMS-20260204-0001
 * 
 * 데이터베이스 기반으로 마지막 번호를 확인하여 중복 방지
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSequenceService {
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final OrderRepository orderRepository;
    
    /**
     * 다음 주문번호 생성
     */
    public synchronized String generateOrderNo() {
        String today = getCurrentDateString();
        
        // 오늘 날짜의 마지막 주문번호 조회
        String lastOrderNo = orderRepository.findLastOrderNoByDate(today);
        
        int nextSeq = 1;
        
        if (lastOrderNo != null && !lastOrderNo.isEmpty()) {
            // OMS-20260209-0001 → 0001 추출
            String[] parts = lastOrderNo.split("-");
            if (parts.length == 3) {
                try {
                    int lastSeq = Integer.parseInt(parts[2]);
                    nextSeq = lastSeq + 1;
                    log.debug("📊 오늘의 마지막 주문번호: {}, 다음 시퀀스: {}", lastOrderNo, nextSeq);
                } catch (NumberFormatException e) {
                    log.warn("⚠️ 주문번호 파싱 실패: {}", lastOrderNo);
                }
            }
        }
        
        String orderNo = String.format("OMS-%s-%04d", today, nextSeq);
        
        log.debug("🔢 주문번호 생성: {}", orderNo);
        
        return orderNo;
    }
    
    /**
     * 현재 날짜 문자열 반환 (YYYYMMDD)
     */
    private String getCurrentDateString() {
        return LocalDate.now().format(DATE_FORMATTER);
    }
}
