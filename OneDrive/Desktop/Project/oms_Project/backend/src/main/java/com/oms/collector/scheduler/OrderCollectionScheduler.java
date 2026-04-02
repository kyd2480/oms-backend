package com.oms.collector.scheduler;

import com.oms.collector.service.OrderCollectionService;
import com.oms.collector.service.OrderProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 주문 수집 스케줄러
 * 
 * 주기적으로 판매처에서 주문을 수집하고 처리합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "collector.schedule.enabled", havingValue = "true", matchIfMissing = true)
public class OrderCollectionScheduler {
    
    private final OrderCollectionService collectionService;
    private final OrderProcessingService processingService;
    
    /**
     * 주기적 주문 수집 (10분마다)
     * 
     * fixedDelay: 이전 작업 완료 후 10분 대기
     * initialDelay: 시작 후 1분 뒤 첫 실행
     */
    @Scheduled(fixedDelayString = "${collector.schedule.interval:600000}", 
               initialDelayString = "${collector.schedule.initial-delay:60000}")
    public void collectRecentOrders() {
        log.info("⏰ ========================================");
        log.info("⏰ 주문 수집 스케줄러 실행");
        log.info("⏰ ========================================");
        
        try {
            // 최근 15분 주문 수집 (중복 방지를 위해 여유있게)
            LocalDateTime endDate = LocalDateTime.now();
            LocalDateTime startDate = endDate.minusMinutes(15);
            
            log.info("📅 수집 기간: {} ~ {}", startDate, endDate);
            
            // 1. 주문 수집
            collectionService.collectAllChannels(startDate, endDate);
            
            // 2. 수집된 주문 자동 처리
            int processedCount = processingService.processUnprocessedOrders();
            
            log.info("✅ 스케줄러 작업 완료 (처리: {} 건)", processedCount);
            
        } catch (Exception e) {
            log.error("❌ 스케줄러 작업 실패", e);
        }
        
        log.info("⏰ ========================================");
    }
    
    /**
     * 매일 자정에 전날 주문 재수집 (누락 방지)
     * 
     * cron: 초 분 시 일 월 요일
     * "0 0 0 * * *" = 매일 자정
     */
    @Scheduled(cron = "${collector.schedule.daily-cron:0 0 0 * * *}")
    public void collectYesterdayOrders() {
        log.info("🌙 ========================================");
        log.info("🌙 전날 주문 재수집 시작");
        log.info("🌙 ========================================");
        
        try {
            // 어제 00:00 ~ 오늘 00:00
            LocalDateTime endDate = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
            LocalDateTime startDate = endDate.minusDays(1);
            
            log.info("📅 재수집 기간: {} ~ {}", startDate, endDate);
            
            // 1. 전날 주문 재수집
            collectionService.collectAllChannels(startDate, endDate);
            
            // 2. 수집된 주문 처리
            int processedCount = processingService.processUnprocessedOrders();
            
            log.info("✅ 전날 주문 재수집 완료 (처리: {} 건)", processedCount);
            
        } catch (Exception e) {
            log.error("❌ 전날 주문 재수집 실패", e);
        }
        
        log.info("🌙 ========================================");
    }
    
    /**
     * 매시간 처리 실패 주문 재시도
     */
    @Scheduled(cron = "${collector.schedule.retry-cron:0 0 * * * *}")
    public void retryFailedOrders() {
        log.info("🔄 ========================================");
        log.info("🔄 실패 주문 재시도 시작");
        log.info("🔄 ========================================");
        
        try {
            // 미처리 주문 재시도
            int processedCount = processingService.processUnprocessedOrders();
            
            if (processedCount > 0) {
                log.info("✅ 실패 주문 재시도 완료 (처리: {} 건)", processedCount);
            } else {
                log.debug("ℹ️ 재시도할 주문 없음");
            }
            
        } catch (Exception e) {
            log.error("❌ 실패 주문 재시도 실패", e);
        }
        
        log.info("🔄 ========================================");
    }
    
    /**
     * 매일 오전 9시 통계 로깅
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void logDailyStats() {
        log.info("📊 ========================================");
        log.info("📊 일일 통계");
        log.info("📊 ========================================");
        
        try {
            OrderProcessingService.ProcessingStats stats = processingService.getStats();
            
            log.info("📈 전체 주문: {} 건", stats.totalOrders());
            log.info("📈 오늘 주문: {} 건", stats.todayOrders());
            log.info("📈 미처리 주문: {} 건", stats.unprocessedOrders());
            
        } catch (Exception e) {
            log.error("❌ 통계 조회 실패", e);
        }
        
        log.info("📊 ========================================");
    }
}
