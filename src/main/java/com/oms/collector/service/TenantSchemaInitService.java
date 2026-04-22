package com.oms.collector.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 신규 회사(테넌트) 스키마를 초기화합니다.
 *
 * 순서:
 *  1. PostgreSQL 스키마 생성
 *  2. public 스키마의 테이블 구조를 새 스키마에 복사 (LIKE … INCLUDING ALL)
 *  3. FK 제약 조건을 새 스키마 내에서 재생성
 *  4. 운영 보정 마이그레이션 실행
 *  5. 기본 마스터 데이터 시드
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantSchemaInitService {

    private final JdbcTemplate jdbc;

    @Transactional
    public void initSchema(String schemaName) {
        validateSchemaName(schemaName);
        log.info("[TenantInit] 스키마 초기화 시작: {}", schemaName);

        createSchema(schemaName);
        copyTables(schemaName);
        recreateForeignKeys(schemaName);
        runOperationalMigrations(schemaName);
        log.info("[TenantInit] 스키마 초기화 완료: {}", schemaName);
    }

    public boolean schemaExists(String schemaName) {
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?",
            Integer.class, schemaName);
        return cnt != null && cnt > 0;
    }

    public boolean hasData(String schemaName) {
        try {
            Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"" + schemaName + "\".orders", Integer.class);
            return cnt != null && cnt > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public List<String> listTenantSchemas() {
        return jdbc.queryForList(
            "SELECT schema_name FROM information_schema.schemata " +
            "WHERE schema_name NOT IN ('public','information_schema','pg_catalog','pg_toast') " +
            "  AND schema_name NOT LIKE 'pg_%' " +
            "ORDER BY schema_name",
            String.class);
    }

    // ── private ──────────────────────────────────────────────────────────────

    private void createSchema(String schema) {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"" + schema + "\"");
        log.info("[TenantInit] 스키마 생성: {}", schema);
    }

    private void copyTables(String schema) {
        List<String> tables = jdbc.queryForList(
            "SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename",
            String.class);

        for (String table : tables) {
            String sql = "CREATE TABLE IF NOT EXISTS \"" + schema + "\".\"" + table + "\" " +
                         "(LIKE public.\"" + table + "\" INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES)";
            try {
                jdbc.execute(sql);
                log.debug("[TenantInit] 테이블 복사: {}.{}", schema, table);
            } catch (Exception e) {
                log.warn("[TenantInit] 테이블 복사 실패 (무시): {}.{} - {}", schema, table, e.getMessage());
            }
        }
    }

    private void recreateForeignKeys(String schema) {
        // public 스키마의 FK 정보를 읽어 새 스키마에서 재생성
        List<Map<String, Object>> fks = jdbc.queryForList("""
            SELECT
                tc.constraint_name,
                tc.table_name,
                kcu.column_name,
                ccu.table_name  AS foreign_table_name,
                ccu.column_name AS foreign_column_name,
                rc.delete_rule
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu
                ON tc.constraint_name = kcu.constraint_name
               AND tc.table_schema    = kcu.table_schema
            JOIN information_schema.referential_constraints rc
                ON tc.constraint_name = rc.constraint_name
            JOIN information_schema.constraint_column_usage ccu
                ON ccu.constraint_name = rc.unique_constraint_name
            WHERE tc.constraint_type = 'FOREIGN KEY'
              AND tc.table_schema    = 'public'
            """);

        for (Map<String, Object> fk : fks) {
            String constraintName  = schema + "_" + fk.get("constraint_name");
            String tableName       = (String) fk.get("table_name");
            String columnName      = (String) fk.get("column_name");
            String foreignTable    = (String) fk.get("foreign_table_name");
            String foreignColumn   = (String) fk.get("foreign_column_name");
            String deleteRule      = (String) fk.get("delete_rule");

            String sql = "ALTER TABLE \"" + schema + "\".\"" + tableName + "\" " +
                         "ADD CONSTRAINT \"" + constraintName + "\" " +
                         "FOREIGN KEY (\"" + columnName + "\") " +
                         "REFERENCES \"" + schema + "\".\"" + foreignTable + "\" (\"" + foreignColumn + "\") " +
                         "ON DELETE " + deleteRule;
            try {
                jdbc.execute(sql);
                log.debug("[TenantInit] FK 생성: {}", constraintName);
            } catch (Exception e) {
                log.warn("[TenantInit] FK 생성 실패 (무시): {} - {}", constraintName, e.getMessage());
            }
        }
    }

    private void runOperationalMigrations(String schema) {
        // orders 테이블 보정
        exec(schema, "ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_hold BOOLEAN DEFAULT FALSE");
        exec(schema, "UPDATE orders SET shipping_hold = FALSE WHERE shipping_hold IS NULL");
        exec(schema, "ALTER TABLE orders ADD COLUMN IF NOT EXISTS hold_reason TEXT");
        exec(schema, "ALTER TABLE orders ADD COLUMN IF NOT EXISTS priority_allocation BOOLEAN DEFAULT FALSE");
        exec(schema, "UPDATE orders SET priority_allocation = FALSE WHERE priority_allocation IS NULL");
        exec(schema, "ALTER TABLE orders ADD COLUMN IF NOT EXISTS allocation_excluded BOOLEAN DEFAULT FALSE");
        exec(schema, "UPDATE orders SET allocation_excluded = FALSE WHERE allocation_excluded IS NULL");
        exec(schema, "ALTER TABLE orders ADD COLUMN IF NOT EXISTS print_type_code VARCHAR(50) DEFAULT 'NORMAL'");
        exec(schema, "UPDATE orders SET print_type_code = 'NORMAL' WHERE print_type_code IS NULL OR print_type_code = ''");
        exec(schema, "ALTER TABLE orders ADD COLUMN IF NOT EXISTS print_type_name VARCHAR(100) DEFAULT '일반건'");
        exec(schema, "UPDATE orders SET print_type_name = '일반건' WHERE print_type_name IS NULL OR print_type_name = ''");
        exec(schema, "ALTER TABLE orders ADD COLUMN IF NOT EXISTS market_sync_status VARCHAR(20) DEFAULT 'NOT_REQUIRED'");
        exec(schema, "UPDATE orders SET market_sync_status = 'NOT_REQUIRED' WHERE market_sync_status IS NULL OR market_sync_status = ''");
        exec(schema, "ALTER TABLE orders ADD COLUMN IF NOT EXISTS market_sync_message TEXT");
        exec(schema, "ALTER TABLE orders ADD COLUMN IF NOT EXISTS market_sync_attempted_at TIMESTAMP");
        exec(schema, "ALTER TABLE orders ADD COLUMN IF NOT EXISTS market_synced_at TIMESTAMP");
        exec(schema, "ALTER TABLE orders ADD COLUMN IF NOT EXISTS merged_into_order_no VARCHAR(100)");
        exec(schema, "ALTER TABLE orders ADD COLUMN IF NOT EXISTS split_from_order_no VARCHAR(100)");

        // order_items 테이블 보정
        exec(schema, "ALTER TABLE order_items ADD COLUMN IF NOT EXISTS cancelled_quantity INTEGER DEFAULT 0");
        exec(schema, "UPDATE order_items SET cancelled_quantity = 0 WHERE cancelled_quantity IS NULL");
        exec(schema, "ALTER TABLE order_items ADD COLUMN IF NOT EXISTS item_status VARCHAR(20) DEFAULT 'ACTIVE'");
        exec(schema, "UPDATE order_items SET item_status = 'ACTIVE' WHERE item_status IS NULL OR item_status = ''");
        exec(schema, "ALTER TABLE order_items ADD COLUMN IF NOT EXISTS cancel_reason TEXT");

        // products 테이블 보정
        exec(schema, "ALTER TABLE products ADD COLUMN IF NOT EXISTS option_code VARCHAR(100)");
        exec(schema, "ALTER TABLE products ADD COLUMN IF NOT EXISTS option_name VARCHAR(255)");
        exec(schema, "ALTER TABLE products ADD COLUMN IF NOT EXISTS vendor_name VARCHAR(100)");
        exec(schema, "ALTER TABLE products ADD COLUMN IF NOT EXISTS note VARCHAR(500)");

        // operational_settings 테이블
        exec(schema, """
            CREATE TABLE IF NOT EXISTS operational_settings (
                setting_key   VARCHAR(100) PRIMARY KEY,
                setting_value TEXT,
                created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )""");

        // print_types 테이블
        exec(schema, """
            CREATE TABLE IF NOT EXISTS print_types (
                print_type_id UUID PRIMARY KEY,
                code          VARCHAR(50)  NOT NULL UNIQUE,
                name          VARCHAR(100) NOT NULL,
                is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
                sort_order    INTEGER      DEFAULT 999,
                description   VARCHAR(500),
                created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at    TIMESTAMP
            )""");

        log.info("[TenantInit] 운영 마이그레이션 완료: {}", schema);
    }

    private void exec(String schema, String sql) {
        try {
            jdbc.execute("SET LOCAL search_path TO \"" + schema + "\"");
            jdbc.execute(sql);
        } catch (Exception e) {
            log.warn("[TenantInit] SQL 실패 (무시): {} - {}", sql.substring(0, Math.min(60, sql.length())), e.getMessage());
        }
    }

    private void validateSchemaName(String name) {
        if (name == null || !name.matches("[a-z][a-z0-9_]{0,62}"))
            throw new IllegalArgumentException("유효하지 않은 스키마명: " + name);
    }
}
