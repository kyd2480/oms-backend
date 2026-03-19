package com.oms.collector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.collector.dto.CollectedOrder;
import com.oms.collector.entity.Order;
import com.oms.collector.entity.RawOrder;
import com.oms.collector.entity.SalesChannel;
import com.oms.collector.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 주문 처리 서비스
 * 
 * 원본 주문(raw_orders) → 정규화된 주문(orders)으로 변환 및 저장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderProcessingService {
    
    private final RawOrderService rawOrderService;
    private final OrderNormalizer orderNormalizer;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;
    
    /**
     * 미처리 주문 일괄 처리
     */
    @Transactional
    public int processUnprocessedOrders() {
        log.info("========================================");
        log.info("🔄 미처리 주문 처리 시작");
        log.info("========================================");
        
        List<RawOrder> unprocessedOrders = rawOrderService.getUnprocessedOrders();
        log.info("📋 미처리 주문: {} 건", unprocessedOrders.size());
        
        int successCount = 0;
        int errorCount = 0;
        
        for (RawOrder rawOrder : unprocessedOrders) {
            try {
                processRawOrder(rawOrder);
                successCount++;
            } catch (Exception e) {
                errorCount++;
                log.error("❌ 주문 처리 실패: {}", rawOrder.getChannelOrderNo(), e);
                rawOrderService.markAsError(rawOrder, e.getMessage());
            }
        }
        
        log.info("========================================");
        log.info("✅ 미처리 주문 처리 완료");
        log.info("  성공: {} 건 / 실패: {} 건", successCount, errorCount);
        log.info("========================================");
        
        return successCount;
    }
    
    /**
     * 단일 원본 주문 처리
     */
    @Transactional
    public Order processRawOrder(RawOrder rawOrder) {
        log.debug("🔄 원본 주문 처리: {}", rawOrder.getChannelOrderNo());
        
        try {
            // 1. JSON → CollectedOrder 변환
            CollectedOrder collectedOrder = objectMapper.readValue(
                rawOrder.getRawData(), 
                CollectedOrder.class
            );
            
            // 2. 정규화
            SalesChannel channel = rawOrder.getChannel();
            Order order = orderNormalizer.normalize(collectedOrder, rawOrder, channel);
            
            // 3. 저장
            Order savedOrder = orderRepository.save(order);
            
            // 4. 원본 주문 처리 완료 표시
            rawOrderService.markAsProcessed(rawOrder);
            
            log.info("✅ 주문 처리 완료: {} → {}", 
                rawOrder.getChannelOrderNo(), 
                savedOrder.getOrderNo()
            );
            
            return savedOrder;
            
        } catch (Exception e) {
            log.error("❌ 주문 처리 실패: {}", rawOrder.getChannelOrderNo(), e);
            throw new RuntimeException("주문 처리 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 특정 판매처의 미처리 주문 처리
     */
    @Transactional
    public int processUnprocessedOrdersByChannel(String channelCode) {
        log.info("🔄 {} 미처리 주문 처리 시작", channelCode);
        
        List<RawOrder> unprocessedOrders = rawOrderService.getUnprocessedOrdersByChannel(channelCode);
        log.info("📋 {} 미처리 주문: {} 건", channelCode, unprocessedOrders.size());
        
        int successCount = 0;
        
        for (RawOrder rawOrder : unprocessedOrders) {
            try {
                processRawOrder(rawOrder);
                successCount++;
            } catch (Exception e) {
                log.error("❌ 주문 처리 실패: {}", rawOrder.getChannelOrderNo(), e);
                rawOrderService.markAsError(rawOrder, e.getMessage());
            }
        }
        
        log.info("✅ {} 미처리 주문 처리 완료: {} 건", channelCode, successCount);
        
        return successCount;
    }
    
    /**
     * 전체 통계 조회
     */
    @Transactional(readOnly = true)
    public ProcessingStats getStats() {
        long totalOrders = orderRepository.count();
        long todayOrders = orderRepository.countTodayOrders();
        long unprocessedOrders = rawOrderService.getUnprocessedOrders().size();
        
        // 판매처별 통계
        List<ChannelStat> channelStats = getChannelStats();
        
        return new ProcessingStats(totalOrders, todayOrders, unprocessedOrders, channelStats);
    }
    
    /**
     * 판매처별 주문 통계
     */
    private List<ChannelStat> getChannelStats() {
        List<Order> allOrders = orderRepository.findAll();
        
        // 판매처별 그룹핑
        Map<String, ChannelStatBuilder> statMap = new HashMap<>();
        
        for (Order order : allOrders) {
            if (order.getChannel() == null) continue;
            
            String channelCode = order.getChannel().getChannelCode();
            String channelName = order.getChannel().getChannelName();
            
            statMap.computeIfAbsent(channelCode, k -> new ChannelStatBuilder(channelCode, channelName))
                .addOrder();
        }
        
        return statMap.values().stream()
            .map(ChannelStatBuilder::build)
            .sorted((a, b) -> Long.compare(b.orderCount(), a.orderCount())) // 주문 많은 순
            .collect(Collectors.toList());
    }
    
    /**
     * 판매처 통계 빌더
     */
    private static class ChannelStatBuilder {
        private final String channelCode;
        private final String channelName;
        private long orderCount = 0;
        
        public ChannelStatBuilder(String channelCode, String channelName) {
            this.channelCode = channelCode;
            this.channelName = channelName;
        }
        
        public ChannelStatBuilder addOrder() {
            this.orderCount++;
            return this;
        }
        
        public ChannelStat build() {
            return new ChannelStat(channelCode, channelName, orderCount);
        }
    }
    
    /**
     * 처리 통계 DTO
     */
    public record ProcessingStats(
        long totalOrders,
        long todayOrders,
        long unprocessedOrders,
        List<ChannelStat> channelStats
    ) {}
    
    /**
     * 판매처별 통계 DTO
     */
    public record ChannelStat(
        String channelCode,
        String channelName,
        long orderCount
    ) {}
}
