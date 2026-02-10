package com.oms.collector.service;

import com.oms.collector.collector.OrderCollector;
import com.oms.collector.dto.CollectedOrder;
import com.oms.collector.entity.RawOrder;
import com.oms.collector.entity.SalesChannel;
import com.oms.collector.repository.SalesChannelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 주문 수집 서비스
 * 
 * 모든 판매처에서 주문을 수집하고 저장하는 메인 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCollectionService {
    
    private final List<OrderCollector> collectors;  // 모든 OrderCollector 구현체가 자동 주입됨
    private final SalesChannelRepository salesChannelRepository;
    private final RawOrderService rawOrderService;
    
    /**
     * 모든 활성 판매처에서 주문 수집
     */
    public void collectAllChannels(LocalDateTime startDate, LocalDateTime endDate) {
        log.info("========================================");
        log.info("🚀 전체 판매처 주문 수집 시작");
        log.info("  기간: {} ~ {}", startDate, endDate);
        log.info("========================================");
        
        // Collector Map 생성 (channelCode -> Collector)
        Map<String, OrderCollector> collectorMap = collectors.stream()
            .collect(Collectors.toMap(OrderCollector::getChannelCode, Function.identity()));
        
        log.info("✅ 등록된 Collector: {}", collectorMap.keySet());
        
        // 활성 판매처 조회
        List<SalesChannel> activeChannels = salesChannelRepository.findByIsActiveTrue();
        log.info("✅ 활성 판매처: {} 개", activeChannels.size());
        
        int totalCollected = 0;
        int totalSaved = 0;
        
        // 각 판매처별로 수집
        for (SalesChannel channel : activeChannels) {
            try {
                OrderCollector collector = collectorMap.get(channel.getChannelCode());
                
                if (collector == null) {
                    log.warn("⚠️ {} Collector가 없습니다. 건너뜁니다.", channel.getChannelCode());
                    continue;
                }
                
                log.info("📦 {} 주문 수집 중...", channel.getChannelName());
                
                // 주문 수집
                List<CollectedOrder> orders = collector.collectOrders(startDate, endDate);
                totalCollected += orders.size();
                
                log.info("  - 수집된 주문: {} 건", orders.size());
                
                // 원본 저장
                int savedCount = 0;
                for (CollectedOrder order : orders) {
                    try {
                        rawOrderService.saveRawOrder(order);
                        savedCount++;
                    } catch (Exception e) {
                        log.error("  - 주문 저장 실패: {}", order.getChannelOrderNo(), e);
                    }
                }
                
                totalSaved += savedCount;
                log.info("  - 저장된 주문: {} 건", savedCount);
                
                // 마지막 수집 시간 업데이트
                channel.updateLastCollectedTime();
                salesChannelRepository.save(channel);
                
            } catch (Exception e) {
                log.error("❌ {} 주문 수집 실패", channel.getChannelName(), e);
            }
        }
        
        log.info("========================================");
        log.info("✅ 전체 판매처 주문 수집 완료");
        log.info("  수집: {} 건 / 저장: {} 건", totalCollected, totalSaved);
        log.info("========================================");
    }
    
    /**
     * 특정 판매처에서 주문 수집
     */
    public List<CollectedOrder> collectByChannel(String channelCode, LocalDateTime startDate, LocalDateTime endDate) {
        log.info("🔹 {} 주문 수집 시작", channelCode);
        
        OrderCollector collector = collectors.stream()
            .filter(c -> c.getChannelCode().equals(channelCode))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Collector를 찾을 수 없습니다: " + channelCode));
        
        List<CollectedOrder> orders = collector.collectOrders(startDate, endDate);
        
        // 원본 저장
        for (CollectedOrder order : orders) {
            try {
                rawOrderService.saveRawOrder(order);
            } catch (Exception e) {
                log.error("주문 저장 실패: {}", order.getChannelOrderNo(), e);
            }
        }
        
        log.info("✅ {} 주문 {} 건 수집 완료", channelCode, orders.size());
        
        return orders;
    }
    
    /**
     * 모든 Collector 상태 확인
     */
    public Map<String, String> getCollectorStatus() {
        return collectors.stream()
            .collect(Collectors.toMap(
                OrderCollector::getChannelCode,
                c -> c.getCollectorType() + " - " + (c.testConnection() ? "연결됨" : "연결 안 됨")
            ));
    }
}
