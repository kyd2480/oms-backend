package com.oms.collector.collector;

import com.oms.collector.dto.CollectedOrder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 수집기 인터페이스
 * 
 * 판매처별로 이 인터페이스를 구현하여 주문을 수집합니다.
 * Mock과 Real 구현체를 쉽게 교체할 수 있도록 설계되었습니다.
 */
public interface OrderCollector {
    
    /**
     * 판매처 코드 반환
     * 
     * @return NAVER, COUPANG, 11ST 등
     */
    String getChannelCode();
    
    /**
     * 주문 수집
     * 
     * @param startDate 시작 날짜
     * @param endDate 종료 날짜
     * @return 수집된 주문 목록
     */
    List<CollectedOrder> collectOrders(LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * 단일 주문 조회
     * 
     * @param channelOrderNo 판매처 주문번호
     * @return 주문 정보
     */
    CollectedOrder getOrder(String channelOrderNo);
    
    /**
     * 연결 테스트
     * 
     * @return 연결 성공 여부
     */
    boolean testConnection();
    
    /**
     * 수집기 타입 (MOCK 또는 REAL)
     * 
     * @return 수집기 타입
     */
    default String getCollectorType() {
        return "MOCK";
    }
}
