package com.logistics.scm.auth.config;

import com.logistics.scm.auth.entity.User;
import com.logistics.scm.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 초기 데이터 생성
 * 
 * 데모 계정 3개 자동 생성:
 * - admin / admin123
 * - manager / manager123
 * - user / user123
 * 
 * @author OMS Team
 * @since 2025-02-03
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public CommandLineRunner initData() {
        return args -> {
            log.info("=== 초기 데이터 생성 시작 ===");

            // 1. Admin 계정 생성
            if (!userRepository.existsByUsername("admin")) {
                User admin = User.create("admin", passwordEncoder.encode("admin123"),
                    "관리자", "admin@oms.com", User.UserRole.ADMIN, "C00");
                userRepository.save(admin);
                log.info("✅ Admin 계정 생성: admin / admin123 (C00)");
            }

            if (!userRepository.existsByUsername("manager")) {
                User manager = User.create("manager", passwordEncoder.encode("manager123"),
                    "매니저", "manager@oms.com", User.UserRole.MANAGER, "C00");
                userRepository.save(manager);
                log.info("✅ Manager 계정 생성: manager / manager123 (C00)");
            }

            if (!userRepository.existsByUsername("user")) {
                User user = User.create("user", passwordEncoder.encode("user123"),
                    "사용자", "user@oms.com", User.UserRole.USER, "C00");
                userRepository.save(user);
                log.info("✅ User 계정 생성: user / user123 (C00)");
            }

            // 기존 계정 중 companyCode가 null인 경우 C00 기본값 설정
            userRepository.findAll().stream()
                .filter(u -> u.getCompanyCode() == null)
                .forEach(u -> { u.setCompanyCode("C00"); userRepository.save(u); });

            log.info("=== 초기 데이터 생성 완료 ===");
            log.info("총 사용자 수: {}", userRepository.count());
        };
    }
}
