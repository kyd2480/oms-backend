package com.logistics.scm.auth.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class AuthSchemaMigration implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        log.info("=== auth-service 스키마 보정 시작 ===");
        execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS phone VARCHAR(20)");
        execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE");
        execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS phone_verified BOOLEAN NOT NULL DEFAULT FALSE");
        execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMP");
        execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS joined_at DATE");
        execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS expires_at DATE");
        execute("UPDATE users SET email_verified = FALSE WHERE email_verified IS NULL");
        execute("UPDATE users SET phone_verified = FALSE WHERE phone_verified IS NULL");
        execute("UPDATE users SET password_changed_at = COALESCE(password_changed_at, created_at, NOW())");
        execute("UPDATE users SET joined_at = COALESCE(joined_at, created_at::date, CURRENT_DATE)");
        execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_users_phone_unique ON users(phone) WHERE phone IS NOT NULL");
        execute("""
            CREATE TABLE IF NOT EXISTS verification_codes (
                verification_id UUID PRIMARY KEY,
                purpose VARCHAR(30) NOT NULL,
                method VARCHAR(20) NOT NULL,
                target_value VARCHAR(120) NOT NULL,
                username VARCHAR(50),
                verification_code VARCHAR(10) NOT NULL,
                used BOOLEAN NOT NULL DEFAULT FALSE,
                verified BOOLEAN NOT NULL DEFAULT FALSE,
                verification_token VARCHAR(80),
                expires_at TIMESTAMP NOT NULL,
                created_at TIMESTAMP NOT NULL,
                used_at TIMESTAMP
            )
            """);
        execute("CREATE INDEX IF NOT EXISTS idx_verification_lookup ON verification_codes(purpose, method, target_value, username, used, expires_at)");
        log.info("=== auth-service 스키마 보정 완료 ===");
    }

    private void execute(String sql) {
        jdbcTemplate.execute(sql);
    }
}
