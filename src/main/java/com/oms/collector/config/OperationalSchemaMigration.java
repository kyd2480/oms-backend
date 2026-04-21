package com.oms.collector.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component("operationalSchemaMigration")
@RequiredArgsConstructor
public class OperationalSchemaMigration {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        migrateOrders();
        migratePrintTypes();
        log.info("운영 스키마 보정 완료");
    }

    private void migrateOrders() {
        execute("ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_hold BOOLEAN");
        execute("ALTER TABLE orders ALTER COLUMN shipping_hold SET DEFAULT FALSE");
        execute("UPDATE orders SET shipping_hold = FALSE WHERE shipping_hold IS NULL");
        execute("ALTER TABLE orders ALTER COLUMN shipping_hold SET NOT NULL");

        execute("ALTER TABLE orders ADD COLUMN IF NOT EXISTS hold_reason TEXT");

        execute("ALTER TABLE orders ADD COLUMN IF NOT EXISTS priority_allocation BOOLEAN");
        execute("ALTER TABLE orders ALTER COLUMN priority_allocation SET DEFAULT FALSE");
        execute("UPDATE orders SET priority_allocation = FALSE WHERE priority_allocation IS NULL");
        execute("ALTER TABLE orders ALTER COLUMN priority_allocation SET NOT NULL");

        execute("ALTER TABLE orders ADD COLUMN IF NOT EXISTS allocation_excluded BOOLEAN");
        execute("ALTER TABLE orders ALTER COLUMN allocation_excluded SET DEFAULT FALSE");
        execute("UPDATE orders SET allocation_excluded = FALSE WHERE allocation_excluded IS NULL");
        execute("ALTER TABLE orders ALTER COLUMN allocation_excluded SET NOT NULL");

        execute("ALTER TABLE orders ADD COLUMN IF NOT EXISTS print_type_code VARCHAR(50)");
        execute("ALTER TABLE orders ALTER COLUMN print_type_code SET DEFAULT 'NORMAL'");
        execute("UPDATE orders SET print_type_code = 'NORMAL' WHERE print_type_code IS NULL OR print_type_code = ''");

        execute("ALTER TABLE orders ADD COLUMN IF NOT EXISTS print_type_name VARCHAR(100)");
        execute("ALTER TABLE orders ALTER COLUMN print_type_name SET DEFAULT '일반건'");
        execute("UPDATE orders SET print_type_name = '일반건' WHERE print_type_name IS NULL OR print_type_name = ''");

        execute("ALTER TABLE orders ADD COLUMN IF NOT EXISTS merged_into_order_no VARCHAR(100)");
        execute("ALTER TABLE orders ADD COLUMN IF NOT EXISTS split_from_order_no VARCHAR(100)");
    }

    private void migratePrintTypes() {
        execute("""
            CREATE TABLE IF NOT EXISTS print_types (
                print_type_id UUID PRIMARY KEY,
                code VARCHAR(50) NOT NULL UNIQUE,
                name VARCHAR(100) NOT NULL,
                is_active BOOLEAN NOT NULL DEFAULT TRUE,
                sort_order INTEGER DEFAULT 999,
                description VARCHAR(500),
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP
            )
            """);
    }

    private void execute(String sql) {
        jdbcTemplate.execute(sql);
    }
}
