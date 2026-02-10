package com.oms.collector.service;

import com.oms.collector.entity.SalesChannel;
import com.oms.collector.repository.SalesChannelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Order Collector Service
 * 
 * 판매처별 주문 수집 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCollectorService {
    
    private final SalesChannelRepository salesChannelRepository;
    
    /**
     * 활성화된 모든 판매처 조회
     */
    @Transactional(readOnly = true)
    public List<SalesChannel> getActiveChannels() {
        log.debug("활성화된 판매처 조회");
        return salesChannelRepository.findByIsActiveTrue();
    }
    
    /**
     * 판매처 코드로 조회
     */
    @Transactional(readOnly = true)
    public SalesChannel getChannelByCode(String channelCode) {
        log.debug("판매처 조회: {}", channelCode);
        return salesChannelRepository.findByChannelCode(channelCode)
            .orElseThrow(() -> new RuntimeException("판매처를 찾을 수 없습니다: " + channelCode));
    }
    
    /**
     * 판매처 등록
     */
    @Transactional
    public SalesChannel registerChannel(SalesChannel channel) {
        log.info("판매처 등록: {}", channel.getChannelCode());
        
        if (salesChannelRepository.existsByChannelCode(channel.getChannelCode())) {
            throw new RuntimeException("이미 등록된 판매처입니다: " + channel.getChannelCode());
        }
        
        return salesChannelRepository.save(channel);
    }
    
    /**
     * 판매처 활성화/비활성화
     */
    @Transactional
    public void toggleChannelStatus(String channelCode) {
        log.info("판매처 상태 변경: {}", channelCode);
        
        SalesChannel channel = getChannelByCode(channelCode);
        channel.setIsActive(!channel.getIsActive());
        salesChannelRepository.save(channel);
    }
    
    /**
     * Health Check
     */
    public String healthCheck() {
        long activeChannelCount = salesChannelRepository.findByIsActiveTrue().size();
        return String.format("Order Collector Service is running. Active channels: %d", activeChannelCount);
    }
}
