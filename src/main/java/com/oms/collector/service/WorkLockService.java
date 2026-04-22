package com.oms.collector.service;

import com.oms.collector.config.TenantContext;
import com.oms.collector.exception.LockConflictException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 동시작업 충돌 방지용 DB 기반 잠금 서비스.
 *
 * - 락 키: "ORDER:{orderNo}" / "INVOICE_SCAN:{trackingNo}"
 * - TTL 만료된 락은 acquire 시점에 자동 정리
 * - 테이블 접근 실패(스키마 미초기화 등)는 경고 후 무시
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkLockService {

    private final JdbcTemplate jdbc;

    /** 현재 테넌트 스키마를 포함한 완전 한정 테이블명 */
    private String table() {
        String schema = TenantContext.getCurrentTenant();
        if (schema == null || schema.isBlank()) schema = "public";
        return "\"" + schema + "\".work_locks";
    }

    /**
     * 락 획득.
     * - TTL 만료 락 자동 삭제 후 INSERT
     * - 이미 유효한 락이 있으면 LockConflictException
     */
    public void acquire(String lockKey, String lockedBy, int ttlSeconds) {
        String t = table();
        try {
            jdbc.update("DELETE FROM " + t + " WHERE lock_key = ? AND expires_at < NOW()", lockKey);
            jdbc.update(
                "INSERT INTO " + t + " (lock_key, locked_by, locked_at, expires_at) " +
                "VALUES (?, ?, NOW(), NOW() + INTERVAL '" + ttlSeconds + " seconds')",
                lockKey, lockedBy
            );
        } catch (DuplicateKeyException e) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT locked_by FROM " + t + " WHERE lock_key = ?", lockKey
            );
            String holder = rows.isEmpty() ? "다른 사용자" : String.valueOf(rows.get(0).get("locked_by"));
            throw new LockConflictException(lockKey, holder);
        } catch (Exception e) {
            log.warn("[WorkLock] 락 테이블 접근 실패 (무시): {} — {}", lockKey, e.getMessage());
        }
    }

    /** 락 해제 */
    public void release(String lockKey) {
        try {
            jdbc.update("DELETE FROM " + table() + " WHERE lock_key = ?", lockKey);
        } catch (Exception e) {
            log.warn("[WorkLock] 락 해제 실패 (무시): {} — {}", lockKey, e.getMessage());
        }
    }
}
