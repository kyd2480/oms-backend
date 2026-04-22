package com.oms.collector.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

/**
 * 신규 회사(테넌트) 스키마 초기화.
 *
 * @Transactional 미사용 — DDL은 PostgreSQL에서 auto-commit되며,
 * Hibernate MultiTenantConnectionProvider와 충돌을 피하기 위해
 * DataSource에서 직접 Connection을 얻어 사용합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantSchemaInitService {

    private final JdbcTemplate jdbc;
    private final DataSource   dataSource;

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
        try {
            Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?",
                Integer.class, schemaName);
            return cnt != null && cnt > 0;
        } catch (Exception e) {
            return false;
        }
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
        // DataSource에서 직접 커넥션을 얻어 DDL 실행 (Hibernate 멀티테넌시 우회)
        try (Connection conn = dataSource.getConnection();
             Statement  stmt = conn.createStatement()) {
            stmt.execute("CREATE SCHEMA IF NOT EXISTS \"" + schema + "\"");
            log.info("[TenantInit] 스키마 생성 완료: {}", schema);
        } catch (Exception e) {
            throw new RuntimeException("스키마 생성 실패: " + schema + " — " + e.getMessage(), e);
        }
    }

    private void copyTables(String schema) {
        List<String> tables = jdbc.queryForList(
            "SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename",
            String.class);

        log.info("[TenantInit] 복사 대상 테이블 수: {}", tables.size());

        for (String table : tables) {
            String sql = "CREATE TABLE IF NOT EXISTS \"" + schema + "\".\"" + table + "\" " +
                         "(LIKE public.\"" + table + "\" INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES)";
            try (Connection conn = dataSource.getConnection();
                 Statement  stmt = conn.createStatement()) {
                stmt.execute(sql);
                log.debug("[TenantInit] 테이블 복사: {}.{}", schema, table);
            } catch (Exception e) {
                log.warn("[TenantInit] 테이블 복사 실패 (무시): {}.{} — {}", schema, table, e.getMessage());
            }
        }
    }

    private void recreateForeignKeys(String schema) {
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
            String constraintName = schema + "_" + fk.get("constraint_name");
            String tableName      = (String) fk.get("table_name");
            String columnName     = (String) fk.get("column_name");
            String foreignTable   = (String) fk.get("foreign_table_name");
            String foreignColumn  = (String) fk.get("foreign_column_name");
            String deleteRule     = (String) fk.get("delete_rule");

            String sql = "ALTER TABLE \"" + schema + "\".\"" + tableName + "\" " +
                         "ADD CONSTRAINT \"" + constraintName + "\" " +
                         "FOREIGN KEY (\"" + columnName + "\") " +
                         "REFERENCES \"" + schema + "\".\"" + foreignTable + "\" (\"" + foreignColumn + "\") " +
                         "ON DELETE " + deleteRule;
            try (Connection conn = dataSource.getConnection();
                 Statement  stmt = conn.createStatement()) {
                stmt.execute(sql);
                log.debug("[TenantInit] FK 생성: {}", constraintName);
            } catch (Exception e) {
                log.warn("[TenantInit] FK 생성 실패 (무시): {} — {}", constraintName, e.getMessage());
            }
        }
    }

    /**
     * 테이블명을 완전 한정자(schema.table)로 지정해 search_path 없이 실행합니다.
     */
    private void runOperationalMigrations(String s) {
        // orders
        exec(s, "orders", "ALTER TABLE \"%s\".orders ADD COLUMN IF NOT EXISTS shipping_hold BOOLEAN DEFAULT FALSE");
        exec(s, "orders", "UPDATE \"%s\".orders SET shipping_hold = FALSE WHERE shipping_hold IS NULL");
        exec(s, "orders", "ALTER TABLE \"%s\".orders ADD COLUMN IF NOT EXISTS hold_reason TEXT");
        exec(s, "orders", "ALTER TABLE \"%s\".orders ADD COLUMN IF NOT EXISTS priority_allocation BOOLEAN DEFAULT FALSE");
        exec(s, "orders", "UPDATE \"%s\".orders SET priority_allocation = FALSE WHERE priority_allocation IS NULL");
        exec(s, "orders", "ALTER TABLE \"%s\".orders ADD COLUMN IF NOT EXISTS allocation_excluded BOOLEAN DEFAULT FALSE");
        exec(s, "orders", "UPDATE \"%s\".orders SET allocation_excluded = FALSE WHERE allocation_excluded IS NULL");
        exec(s, "orders", "ALTER TABLE \"%s\".orders ADD COLUMN IF NOT EXISTS print_type_code VARCHAR(50) DEFAULT 'NORMAL'");
        exec(s, "orders", "UPDATE \"%s\".orders SET print_type_code = 'NORMAL' WHERE print_type_code IS NULL OR print_type_code = ''");
        exec(s, "orders", "ALTER TABLE \"%s\".orders ADD COLUMN IF NOT EXISTS print_type_name VARCHAR(100) DEFAULT '일반건'");
        exec(s, "orders", "UPDATE \"%s\".orders SET print_type_name = '일반건' WHERE print_type_name IS NULL OR print_type_name = ''");
        exec(s, "orders", "ALTER TABLE \"%s\".orders ADD COLUMN IF NOT EXISTS market_sync_status VARCHAR(20) DEFAULT 'NOT_REQUIRED'");
        exec(s, "orders", "UPDATE \"%s\".orders SET market_sync_status = 'NOT_REQUIRED' WHERE market_sync_status IS NULL OR market_sync_status = ''");
        exec(s, "orders", "ALTER TABLE \"%s\".orders ADD COLUMN IF NOT EXISTS market_sync_message TEXT");
        exec(s, "orders", "ALTER TABLE \"%s\".orders ADD COLUMN IF NOT EXISTS market_sync_attempted_at TIMESTAMP");
        exec(s, "orders", "ALTER TABLE \"%s\".orders ADD COLUMN IF NOT EXISTS market_synced_at TIMESTAMP");
        exec(s, "orders", "ALTER TABLE \"%s\".orders ADD COLUMN IF NOT EXISTS merged_into_order_no VARCHAR(100)");
        exec(s, "orders", "ALTER TABLE \"%s\".orders ADD COLUMN IF NOT EXISTS split_from_order_no VARCHAR(100)");

        // order_items
        exec(s, "order_items", "ALTER TABLE \"%s\".order_items ADD COLUMN IF NOT EXISTS cancelled_quantity INTEGER DEFAULT 0");
        exec(s, "order_items", "UPDATE \"%s\".order_items SET cancelled_quantity = 0 WHERE cancelled_quantity IS NULL");
        exec(s, "order_items", "ALTER TABLE \"%s\".order_items ADD COLUMN IF NOT EXISTS item_status VARCHAR(20) DEFAULT 'ACTIVE'");
        exec(s, "order_items", "UPDATE \"%s\".order_items SET item_status = 'ACTIVE' WHERE item_status IS NULL OR item_status = ''");
        exec(s, "order_items", "ALTER TABLE \"%s\".order_items ADD COLUMN IF NOT EXISTS cancel_reason TEXT");

        // products
        exec(s, "products", "ALTER TABLE \"%s\".products ADD COLUMN IF NOT EXISTS option_code VARCHAR(100)");
        exec(s, "products", "ALTER TABLE \"%s\".products ADD COLUMN IF NOT EXISTS option_name VARCHAR(255)");
        exec(s, "products", "ALTER TABLE \"%s\".products ADD COLUMN IF NOT EXISTS vendor_name VARCHAR(100)");
        exec(s, "products", "ALTER TABLE \"%s\".products ADD COLUMN IF NOT EXISTS note VARCHAR(500)");

        // operational_settings
        execRaw(s, String.format("""
            CREATE TABLE IF NOT EXISTS "%s".operational_settings (
                setting_key   VARCHAR(100) PRIMARY KEY,
                setting_value TEXT,
                created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )""", s));

        // print_types
        execRaw(s, String.format("""
            CREATE TABLE IF NOT EXISTS "%s".print_types (
                print_type_id UUID PRIMARY KEY,
                code          VARCHAR(50)  NOT NULL UNIQUE,
                name          VARCHAR(100) NOT NULL,
                is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
                sort_order    INTEGER      DEFAULT 999,
                description   VARCHAR(500),
                created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at    TIMESTAMP
            )""", s));

        // sabangnet_integrations
        execRaw(s, String.format("""
            CREATE TABLE IF NOT EXISTS "%s".sabangnet_integrations (
                integration_id UUID PRIMARY KEY,
                company_code VARCHAR(20) NOT NULL,
                integration_name VARCHAR(100) NOT NULL,
                sabangnet_id VARCHAR(100) NOT NULL,
                api_key VARCHAR(500) NOT NULL,
                api_base_url VARCHAR(300) NOT NULL,
                logistics_place_id VARCHAR(100),
                enabled BOOLEAN NOT NULL DEFAULT TRUE,
                memo VARCHAR(500),
                last_collected_at TIMESTAMP,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP
            )""", s));
        execRaw(s, String.format("CREATE INDEX IF NOT EXISTS idx_%s_sabangnet_company_code ON \"%s\".sabangnet_integrations(company_code)", s, s));
        execRaw(s, String.format("CREATE UNIQUE INDEX IF NOT EXISTS uk_%s_sabangnet_company_id ON \"%s\".sabangnet_integrations(company_code, sabangnet_id)", s, s));

        // invoice_api_logs
        execRaw(s, String.format("""
            CREATE TABLE IF NOT EXISTS "%s".invoice_api_logs (
                log_id UUID PRIMARY KEY,
                order_no VARCHAR(100),
                tracking_no VARCHAR(100),
                carrier_code VARCHAR(50),
                carrier_name VARCHAR(100),
                action_type VARCHAR(30) NOT NULL,
                api_provider VARCHAR(50),
                success BOOLEAN NOT NULL,
                response_code VARCHAR(100),
                response_message TEXT,
                raw_response TEXT,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )""", s));
        execRaw(s, String.format("CREATE INDEX IF NOT EXISTS idx_%s_invoice_api_logs_order_no ON \"%s\".invoice_api_logs(order_no)", s, s));
        execRaw(s, String.format("CREATE INDEX IF NOT EXISTS idx_%s_invoice_api_logs_tracking_no ON \"%s\".invoice_api_logs(tracking_no)", s, s));
        execRaw(s, String.format("CREATE INDEX IF NOT EXISTS idx_%s_invoice_api_logs_created_at ON \"%s\".invoice_api_logs(created_at)", s, s));

        log.info("[TenantInit] 운영 마이그레이션 완료: {}", s);
    }

    /** sql 템플릿의 %s 자리에 schema 대입 후 실행 */
    private void exec(String schema, String tableHint, String sqlTemplate) {
        String sql = String.format(sqlTemplate, schema);
        execRaw(schema, sql);
    }

    private void execRaw(String schema, String sql) {
        try (Connection conn = dataSource.getConnection();
             Statement  stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (Exception e) {
            log.warn("[TenantInit] SQL 실패 (무시): {} — {}",
                sql.substring(0, Math.min(80, sql.length())).replaceAll("\\s+", " "),
                e.getMessage());
        }
    }

    private void validateSchemaName(String name) {
        if (name == null || !name.matches("[a-z][a-z0-9_]{0,62}"))
            throw new IllegalArgumentException("유효하지 않은 스키마명: " + name);
    }
}
