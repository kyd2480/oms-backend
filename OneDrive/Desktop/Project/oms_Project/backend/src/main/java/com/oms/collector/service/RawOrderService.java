package com.oms.collector.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.collector.dto.CollectedOrder;
import com.oms.collector.entity.RawOrder;
import com.oms.collector.entity.SalesChannel;
import com.oms.collector.repository.RawOrderRepository;
import com.oms.collector.repository.SalesChannelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 원본 주문 저장 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RawOrderService {
    
    private final RawOrderRepository rawOrderRepository;
    private final SalesChannelRepository salesChannelRepository;
    private final ObjectMapper objectMapper;
    
    /**
     * 수집된 주문을 원본 테이블에 저장
     */
    @Transactional
    public RawOrder saveRawOrder(CollectedOrder collectedOrder) {
        log.debug("원본 주문 저장: {}", collectedOrder.getChannelOrderNo());
        
        try {
            // 판매처 조회
            SalesChannel channel = salesChannelRepository.findByChannelCode(collectedOrder.getChannelCode())
                .orElseThrow(() -> new RuntimeException("판매처를 찾을 수 없습니다: " + collectedOrder.getChannelCode()));
            
            // 중복 체크
            if (rawOrderRepository.existsByChannelAndChannelOrderNo(channel, collectedOrder.getChannelOrderNo())) {
                log.warn("⚠️ 이미 존재하는 주문: {}", collectedOrder.getChannelOrderNo());
                return rawOrderRepository.findByChannelAndChannelOrderNo(channel, collectedOrder.getChannelOrderNo())
                    .orElseThrow();
            }
            
            // JSON 변환
            String rawJson = collectedOrder.getRawJson();
            if (rawJson == null || rawJson.isEmpty()) {
                rawJson = objectMapper.writeValueAsString(collectedOrder);
            }
            
            // RawOrder 생성
            RawOrder rawOrder = RawOrder.builder()
                .channel(channel)
                .channelOrderNo(collectedOrder.getChannelOrderNo())
                .rawData(rawJson)
                .collectedAt(LocalDateTime.now())
                .processed(false)
                .build();
            
            RawOrder saved = rawOrderRepository.save(rawOrder);
            log.info("✅ 원본 주문 저장 완료: {} (ID: {})", saved.getChannelOrderNo(), saved.getRawOrderId());
            
            return saved;
            
        } catch (JsonProcessingException e) {
            log.error("❌ JSON 변환 실패", e);
            throw new RuntimeException("JSON 변환 실패", e);
        }
    }
    
    /**
     * 미처리 주문 조회
     */
    @Transactional(readOnly = true)
    public List<RawOrder> getUnprocessedOrders() {
        return rawOrderRepository.findByProcessedFalseOrderByCollectedAtAsc();
    }
    
    /**
     * 판매처별 미처리 주문 조회
     */
    @Transactional(readOnly = true)
    public List<RawOrder> getUnprocessedOrdersByChannel(String channelCode) {
        SalesChannel channel = salesChannelRepository.findByChannelCode(channelCode)
            .orElseThrow(() -> new RuntimeException("판매처를 찾을 수 없습니다"));
        
        return rawOrderRepository.findByChannelAndProcessedFalseOrderByCollectedAtAsc(channel);
    }
    
    /**
     * 주문 처리 완료 표시
     */
    @Transactional
    public void markAsProcessed(RawOrder rawOrder) {
        rawOrder.markAsProcessed();
        rawOrderRepository.save(rawOrder);
        log.debug("주문 처리 완료 표시: {}", rawOrder.getChannelOrderNo());
    }
    
    /**
     * 주문 처리 에러 표시
     */
    @Transactional
    public void markAsError(RawOrder rawOrder, String errorMessage) {
        rawOrder.markAsError(errorMessage);
        rawOrderRepository.save(rawOrder);
        log.error("주문 처리 에러 표시: {} - {}", rawOrder.getChannelOrderNo(), errorMessage);
    }
}
