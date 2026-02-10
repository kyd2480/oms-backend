package com.oms.collector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Order Collector Service
 * 
 * 판매처별 주문 수집 서비스
 * 
 * @author OMS Team
 * @since 2025-02-04
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
public class OrderCollectorApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(OrderCollectorApplication.class, args);
    }
}
