package com.logistics.scm.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Auth Service Application
 * 
 * JWT 기반 인증 서비스
 * 
 * @author OMS Team
 * @since 2025-02-03
 */
@SpringBootApplication
@EnableJpaAuditing
public class AuthServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
