package com.logistics.scm.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MaintenanceService {

    private static final String DEFAULT_TITLE = "시스템 점검 안내";
    private static final String DEFAULT_MESSAGE = "보다 안정적인 서비스 제공을 위해 점검 중입니다. 잠시 후 다시 접속해주세요.";
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");

    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public Map<String, Object> getSettings() {
        return jdbcTemplate.queryForObject("""
            SELECT enabled, start_at, end_at, title, message, updated_at
            FROM app_maintenance_settings
            WHERE id = 1
            """, (rs, rowNum) -> {
            LocalDateTime startAt = toLocalDateTime(rs.getTimestamp("start_at"));
            LocalDateTime endAt = toLocalDateTime(rs.getTimestamp("end_at"));
            String title = rs.getString("title");
            String message = rs.getString("message");
            boolean enabled = rs.getBoolean("enabled");
            return Map.of(
                "enabled", enabled,
                "startAt", startAt,
                "endAt", endAt,
                "title", title == null || title.isBlank() ? DEFAULT_TITLE : title,
                "message", message == null || message.isBlank() ? DEFAULT_MESSAGE : message,
                "updatedAt", toLocalDateTime(rs.getTimestamp("updated_at")),
                "active", isActive(enabled, startAt, endAt)
            );
        });
    }

    @Transactional
    public Map<String, Object> updateSettings(Boolean enabled, LocalDateTime startAt, LocalDateTime endAt, String title, String message) {
        if (enabled == null) {
            throw new RuntimeException("enabled 값이 필요합니다.");
        }
        if (enabled && startAt == null) {
            throw new RuntimeException("점검 사용 시 시작시간은 필수입니다.");
        }
        if (enabled && endAt == null) {
            throw new RuntimeException("점검 사용 시 종료시간은 필수입니다.");
        }
        if (startAt != null && endAt != null && endAt.isBefore(startAt)) {
            throw new RuntimeException("종료시간은 시작시간보다 빠를 수 없습니다.");
        }
        jdbcTemplate.update("""
            UPDATE app_maintenance_settings
            SET enabled = ?, start_at = ?, end_at = ?, title = ?, message = ?, updated_at = NOW()
            WHERE id = 1
            """,
            enabled,
            toTimestamp(startAt),
            toTimestamp(endAt),
            normalizeText(title, DEFAULT_TITLE),
            normalizeText(message, DEFAULT_MESSAGE)
        );
        return getSettings();
    }

    private boolean isActive(boolean enabled, LocalDateTime startAt, LocalDateTime endAt) {
        if (!enabled || startAt == null || endAt == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now(APP_ZONE);
        return (!now.isBefore(startAt)) && (!now.isAfter(endAt));
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private Timestamp toTimestamp(LocalDateTime time) {
        return time == null ? null : Timestamp.valueOf(time);
    }

    private String normalizeText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
