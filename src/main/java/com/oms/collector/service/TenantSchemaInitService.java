package com.oms.collector.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

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
    private final ObjectMapper objectMapper;

    private static final Set<String> BACKUP_EXCLUDED_TABLES = Set.of("work_locks");

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

    public Map<String, Object> exportSchemaBackup(String schemaName) {
        validateSchemaName(schemaName);
        if (!schemaExists(schemaName)) {
            throw new IllegalArgumentException("존재하지 않는 스키마입니다: " + schemaName);
        }

        List<String> tables = jdbc.queryForList(
            "SELECT tablename FROM pg_tables WHERE schemaname = ? ORDER BY tablename",
            String.class,
            schemaName
        );

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("schema", schemaName);
        meta.put("companyCode", "public".equals(schemaName) ? "C00" : schemaName.toUpperCase());
        meta.put("generatedAt", LocalDateTime.now().toString());
        meta.put("tableCount", tables.size());

        List<Map<String, Object>> tableSummaries = new ArrayList<>();
        Map<String, Object> data = new LinkedHashMap<>();

        for (String table : tables) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM \"" + schemaName + "\".\"" + table + "\""
            );
            data.put(table, rows);

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("table", table);
            summary.put("rows", rows.size());
            tableSummaries.add(summary);
        }

        meta.put("tables", tableSummaries);

        Map<String, Object> backup = new LinkedHashMap<>();
        backup.put("meta", meta);
        backup.put("data", data);
        return backup;
    }

    public Map<String, Object> restoreSchemaBackup(String schemaName, MultipartFile file) {
        validateSchemaName(schemaName);
        try {
            Map<String, Object> backup = objectMapper.readValue(
                openBackupInputStream(file),
                new TypeReference<>() {}
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) backup.get("data");
            if (data == null || data.isEmpty()) {
                throw new IllegalArgumentException("백업 파일에 복구할 데이터가 없습니다.");
            }

            if (!schemaExists(schemaName)) {
                initSchema(schemaName);
            }

            List<String> candidateTables = new ArrayList<>(data.keySet());
            candidateTables.removeIf(BACKUP_EXCLUDED_TABLES::contains);
            List<String> existingTables = jdbc.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = ? ORDER BY tablename",
                String.class,
                schemaName
            );
            Set<String> existingTableSet = new HashSet<>(existingTables);
            candidateTables.removeIf(table -> !existingTableSet.contains(table));

            List<String> orderedTables = sortTablesForRestore(schemaName, candidateTables);

            Map<String, Object> restoredSummary = new LinkedHashMap<>();
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    truncateTables(conn, schemaName, orderedTables);
                    for (String table : orderedTables) {
                        Object rowsObj = data.get(table);
                        if (!(rowsObj instanceof Collection<?> rowsCollection)) {
                            restoredSummary.put(table, 0);
                            continue;
                        }
                        int inserted = restoreTableRows(conn, schemaName, table, rowsCollection);
                        restoredSummary.put(table, inserted);
                    }
                    conn.commit();
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            }

            return Map.of(
                "success", true,
                "schema", schemaName,
                "restoredTables", restoredSummary,
                "message", "복구 완료: " + schemaName
            );
        } catch (Exception e) {
            throw new RuntimeException("복구 실패: " + e.getMessage(), e);
        }
    }

    private InputStream openBackupInputStream(MultipartFile file) throws Exception {
        byte[] bytes = file.getBytes();
        if (bytes.length >= 2 && (bytes[0] == (byte) 0x1f) && (bytes[1] == (byte) 0x8b)) {
            return new GZIPInputStream(new ByteArrayInputStream(bytes));
        }
        return new ByteArrayInputStream(bytes);
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
        exec(s, "products", "ALTER TABLE \"%s\".products ADD COLUMN IF NOT EXISTS barcode2 VARCHAR(100)");
        exec(s, "products", "ALTER TABLE \"%s\".products ALTER COLUMN barcode2 TYPE VARCHAR(100) USING barcode2::VARCHAR");
        exec(s, "products", "ALTER TABLE \"%s\".products ADD COLUMN IF NOT EXISTS color VARCHAR(100)");
        exec(s, "products", "ALTER TABLE \"%s\".products ALTER COLUMN color TYPE VARCHAR(100) USING color::VARCHAR");

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
                mall_code VARCHAR(100),
                mall_name VARCHAR(100),
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
        execRaw(s, String.format("CREATE UNIQUE INDEX IF NOT EXISTS uk_%s_sabangnet_company_mall ON \"%s\".sabangnet_integrations(company_code, sabangnet_id, mall_code) WHERE mall_code IS NOT NULL", s, s));

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

        // carrier_contracts
        execRaw(s, String.format("""
            CREATE TABLE IF NOT EXISTS "%s".carrier_contracts (
                contract_id UUID PRIMARY KEY,
                company_code VARCHAR(20) NOT NULL,
                carrier_code VARCHAR(50) NOT NULL,
                carrier_name VARCHAR(100) NOT NULL,
                contract_name VARCHAR(100) NOT NULL,
                is_default BOOLEAN NOT NULL DEFAULT FALSE,
                enabled BOOLEAN NOT NULL DEFAULT TRUE,
                api_base_url VARCHAR(300),
                auth_key VARCHAR(500),
                seed_key VARCHAR(500),
                customer_no VARCHAR(100),
                contract_approval_no VARCHAR(100),
                office_ser VARCHAR(50),
                content_code VARCHAR(50),
                sender_company_name VARCHAR(100),
                sender_tel VARCHAR(50),
                sender_zip VARCHAR(20),
                sender_address VARCHAR(300),
                sender_address_detail VARCHAR(300),
                test_yn VARCHAR(1),
                print_yn VARCHAR(1),
                memo VARCHAR(500),
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP
            )""", s));
        execRaw(s, String.format("CREATE INDEX IF NOT EXISTS idx_%s_carrier_contracts_company_carrier ON \"%s\".carrier_contracts(company_code, carrier_code)", s, s));

        // work_locks
        execRaw(s, String.format("""
            CREATE TABLE IF NOT EXISTS "%s".work_locks (
                lock_key   VARCHAR(200) PRIMARY KEY,
                locked_by  VARCHAR(100) NOT NULL,
                locked_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                expires_at TIMESTAMP    NOT NULL
            )""", s));
        execRaw(s, String.format("CREATE INDEX IF NOT EXISTS idx_%s_work_locks_expires_at ON \"%s\".work_locks(expires_at)", s, s));

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

    private void truncateTables(Connection conn, String schemaName, List<String> orderedTables) throws Exception {
        if (orderedTables.isEmpty()) return;
        String joined = orderedTables.stream()
            .map(table -> "\"" + schemaName + "\".\"" + table + "\"")
            .collect(Collectors.joining(", "));
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE " + joined + " RESTART IDENTITY CASCADE");
        }
    }

    private int restoreTableRows(Connection conn, String schemaName, String table, Collection<?> rowsCollection) throws Exception {
        if (rowsCollection.isEmpty()) return 0;

        List<Map<String, ColumnMeta>> columns = List.of(loadColumnMeta(schemaName, table));
        Map<String, ColumnMeta> metaByName = columns.get(0);
        List<String> orderedColumnNames = metaByName.values().stream()
            .sorted(Comparator.comparingInt(ColumnMeta::ordinalPosition))
            .map(ColumnMeta::columnName)
            .toList();

        int inserted = 0;
        for (Object rowObj : rowsCollection) {
            if (!(rowObj instanceof Map<?, ?> rawMap)) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            rawMap.forEach((k, v) -> row.put(String.valueOf(k), v));

            List<String> insertColumns = orderedColumnNames.stream()
                .filter(row::containsKey)
                .toList();
            if (insertColumns.isEmpty()) continue;

            String sql = buildInsertSql(schemaName, table, insertColumns, metaByName);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < insertColumns.size(); i++) {
                    String columnName = insertColumns.get(i);
                    Object value = row.get(columnName);
                    ps.setObject(i + 1, normalizeBackupValue(value, metaByName.get(columnName)));
                }
                ps.executeUpdate();
                inserted++;
            }
        }
        return inserted;
    }

    private Map<String, ColumnMeta> loadColumnMeta(String schemaName, String table) {
        return jdbc.query(
            """
            SELECT column_name, data_type, udt_name, ordinal_position
            FROM information_schema.columns
            WHERE table_schema = ? AND table_name = ?
            ORDER BY ordinal_position
            """,
            rs -> {
                Map<String, ColumnMeta> map = new LinkedHashMap<>();
                while (rs.next()) {
                    ColumnMeta meta = new ColumnMeta(
                        rs.getString("column_name"),
                        rs.getString("data_type"),
                        rs.getString("udt_name"),
                        rs.getInt("ordinal_position")
                    );
                    map.put(meta.columnName(), meta);
                }
                return map;
            },
            schemaName,
            table
        );
    }

    private String buildInsertSql(String schemaName, String table, List<String> columns, Map<String, ColumnMeta> metaByName) {
        String columnSql = columns.stream()
            .map(name -> "\"" + name + "\"")
            .collect(Collectors.joining(", "));
        String valuesSql = columns.stream()
            .map(name -> placeholderFor(metaByName.get(name)))
            .collect(Collectors.joining(", "));
        return "INSERT INTO \"" + schemaName + "\".\"" + table + "\" (" + columnSql + ") VALUES (" + valuesSql + ")";
    }

    private String placeholderFor(ColumnMeta meta) {
        if (meta == null) return "?";
        String udt = meta.udtName();
        String dataType = meta.dataType();
        if ("uuid".equalsIgnoreCase(udt)) return "CAST(? AS uuid)";
        if ("jsonb".equalsIgnoreCase(udt)) return "CAST(? AS jsonb)";
        if ("json".equalsIgnoreCase(udt)) return "CAST(? AS json)";
        if ("date".equalsIgnoreCase(dataType)) return "CAST(? AS date)";
        if (dataType != null && dataType.startsWith("timestamp")) return "CAST(? AS timestamp)";
        return "?";
    }

    private Object normalizeBackupValue(Object value, ColumnMeta meta) throws Exception {
        if (value == null) return null;
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            return objectMapper.writeValueAsString(value);
        }
        return value;
    }

    private List<String> sortTablesForRestore(String schemaName, List<String> tables) {
        Set<String> tableSet = new LinkedHashSet<>(tables);
        Map<String, Set<String>> outgoing = new HashMap<>();
        Map<String, Integer> indegree = new HashMap<>();
        tableSet.forEach(table -> {
            outgoing.put(table, new LinkedHashSet<>());
            indegree.put(table, 0);
        });

        List<Map<String, Object>> foreignKeys = jdbc.queryForList(
            """
            SELECT
                tc.table_name AS child_table,
                ccu.table_name AS parent_table
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu
              ON tc.constraint_name = kcu.constraint_name
             AND tc.table_schema = kcu.table_schema
            JOIN information_schema.constraint_column_usage ccu
              ON ccu.constraint_name = tc.constraint_name
             AND ccu.constraint_schema = tc.table_schema
            WHERE tc.constraint_type = 'FOREIGN KEY'
              AND tc.table_schema = ?
            """,
            schemaName
        );

        for (Map<String, Object> fk : foreignKeys) {
            String child = String.valueOf(fk.get("child_table"));
            String parent = String.valueOf(fk.get("parent_table"));
            if (!tableSet.contains(child) || !tableSet.contains(parent) || child.equals(parent)) continue;
            if (outgoing.get(parent).add(child)) {
                indegree.put(child, indegree.get(child) + 1);
            }
        }

        List<String> ordered = new ArrayList<>();
        List<String> ready = indegree.entrySet().stream()
            .filter(e -> e.getValue() == 0)
            .map(Map.Entry::getKey)
            .sorted()
            .collect(Collectors.toCollection(ArrayList::new));

        while (!ready.isEmpty()) {
            String current = ready.remove(0);
            ordered.add(current);
            List<String> children = outgoing.getOrDefault(current, Set.of()).stream().sorted().toList();
            for (String child : children) {
                int next = indegree.get(child) - 1;
                indegree.put(child, next);
                if (next == 0) {
                    ready.add(child);
                    ready.sort(String::compareTo);
                }
            }
        }

        if (ordered.size() < tableSet.size()) {
            tableSet.stream()
                .filter(table -> !ordered.contains(table))
                .sorted()
                .forEach(ordered::add);
        }
        return ordered;
    }

    private record ColumnMeta(String columnName, String dataType, String udtName, int ordinalPosition) {}
}


