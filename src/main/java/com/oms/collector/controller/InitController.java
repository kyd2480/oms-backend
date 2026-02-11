package com.oms.collector.controller;

import com.oms.collector.entity.SalesChannel;
import com.oms.collector.repository.SalesChannelRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 데이터 초기화 컨트롤러
 * 
 * 개발/테스트용 - 프로덕션에서는 제거 권장
 */
@RestController
@RequestMapping("/api/init")
public class InitController {
    
    private final SalesChannelRepository salesChannelRepository;
    
    public InitController(SalesChannelRepository salesChannelRepository) {
        this.salesChannelRepository = salesChannelRepository;
    }
    
    /**
     * 판매처 초기 데이터 생성
     */
    @PostMapping("/sales-channels")
    public ResponseEntity<?> initSalesChannels() {
        // 이미 데이터가 있는지 확인
        if (salesChannelRepository.count() > 0) {
            return ResponseEntity.ok()
                .body(String.format("Already initialized: %d channels exist", 
                    salesChannelRepository.count()));
        }
        
        LocalDateTime now = LocalDateTime.now();
        List<SalesChannel> channels = new ArrayList<>();
        
        // 네이버 스마트스토어
        SalesChannel naver = SalesChannel.builder()
            .channelId(UUID.randomUUID())
            .channelCode("NAVER")
            .channelName("네이버 스마트스토어")
            .apiType("REST")
            .isActive(true)
            .createdAt(now)
            .updatedAt(now)
            .build();
        channels.add(naver);
        
        // 쿠팡
        SalesChannel coupang = SalesChannel.builder()
            .channelId(UUID.randomUUID())
            .channelCode("COUPANG")
            .channelName("쿠팡")
            .apiType("REST")
            .isActive(true)
            .createdAt(now)
            .updatedAt(now)
            .build();
        channels.add(coupang);
        
        // 11번가
        SalesChannel st11 = SalesChannel.builder()
            .channelId(UUID.randomUUID())
            .channelCode("11ST")
            .channelName("11번가")
            .apiType("REST")
            .isActive(true)
            .createdAt(now)
            .updatedAt(now)
            .build();
        channels.add(st11);
        
        // 저장
        salesChannelRepository.saveAll(channels);
        
        return ResponseEntity.ok()
            .body(String.format("Successfully initialized %d sales channels", channels.size()));
    }
}
