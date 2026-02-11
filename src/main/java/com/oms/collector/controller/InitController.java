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
        
        List<SalesChannel> channels = new ArrayList<>();
        
        // 네이버 스마트스토어
        SalesChannel naver = new SalesChannel();
        naver.setChannelId(UUID.randomUUID());
        naver.setChannelCode("NAVER");
        naver.setChannelName("네이버 스마트스토어");
        naver.setApiType("REST");
        naver.setActive(true);
        naver.setCreatedAt(LocalDateTime.now());
        naver.setUpdatedAt(LocalDateTime.now());
        channels.add(naver);
        
        // 쿠팡
        SalesChannel coupang = new SalesChannel();
        coupang.setChannelId(UUID.randomUUID());
        coupang.setChannelCode("COUPANG");
        coupang.setChannelName("쿠팡");
        coupang.setApiType("REST");
        coupang.setActive(true);
        coupang.setCreatedAt(LocalDateTime.now());
        coupang.setUpdatedAt(LocalDateTime.now());
        channels.add(coupang);
        
        // 11번가
        SalesChannel st11 = new SalesChannel();
        st11.setChannelId(UUID.randomUUID());
        st11.setChannelCode("11ST");
        st11.setChannelName("11번가");
        st11.setApiType("REST");
        st11.setActive(true);
        st11.setCreatedAt(LocalDateTime.now());
        st11.setUpdatedAt(LocalDateTime.now());
        channels.add(st11);
        
        // 저장
        salesChannelRepository.saveAll(channels);
        
        return ResponseEntity.ok()
            .body(String.format("Successfully initialized %d sales channels", channels.size()));
    }
}
