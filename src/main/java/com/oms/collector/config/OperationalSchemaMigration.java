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
        migrateOperationalSettings();
        migrateOrders();
        migrateOrderItems();
        migrateProducts();
        migratePrintTypes();
        log.info("운영 스키마 보정 완료");
    }

    private void migrateOperationalSettings() {
        execute("""
            CREATE TABLE IF NOT EXISTS operational_settings (
                setting_key VARCHAR(100) PRIMARY KEY,
                setting_value TEXT,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """);
    }

    private void migrateOrders() {
        execute("ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_hold BOOLEAN");
        execute("ALTER TABLE orders ALTER COLUMN shipping_hold SET DEFAULT FALSE");
        execute("UPDATE orders SET shipping_hold = FALSE WHERE shipping_hold IS NULL");
        execute("ALTER TABLE orders ALTER COLUMN shipping_hold SET NOT NULL");

        execute("ALTER TABLE orders ADD COLUMN IF NOT EXISTS hold_reason TEXT");

        execute("ALTER TABLE orders ADD COLUMN IF NOT EXISTS market_sync_status VARCHAR(20)");
        execute("ALTER TABLE orders ALTER COLUMN market_sync_status SET DEFAULT 'NOT_REQUIRED'");
        execute("UPDATE orders SET market_sync_status = 'NOT_REQUIRED' WHERE market_sync_status IS NULL OR market_sync_status = ''");
        execute("ALTER TABLE orders ADD COLUMN IF NOT EXISTS market_sync_message TEXT");
        execute("ALTER TABLE orders ADD COLUMN IF NOT EXISTS market_sync_attempted_at TIMESTAMP");
        execute("ALTER TABLE orders ADD COLUMN IF NOT EXISTS market_synced_at TIMESTAMP");

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

    private void migrateOrderItems() {
        execute("ALTER TABLE order_items ADD COLUMN IF NOT EXISTS cancelled_quantity INTEGER");
        execute("ALTER TABLE order_items ALTER COLUMN cancelled_quantity SET DEFAULT 0");
        execute("UPDATE order_items SET cancelled_quantity = 0 WHERE cancelled_quantity IS NULL");
        execute("ALTER TABLE order_items ALTER COLUMN cancelled_quantity SET NOT NULL");

        execute("ALTER TABLE order_items ADD COLUMN IF NOT EXISTS item_status VARCHAR(20)");
        execute("ALTER TABLE order_items ALTER COLUMN item_status SET DEFAULT 'ACTIVE'");
        execute("UPDATE order_items SET item_status = 'ACTIVE' WHERE item_status IS NULL OR item_status = ''");
        execute("ALTER TABLE order_items ALTER COLUMN item_status SET NOT NULL");

        execute("ALTER TABLE order_items ADD COLUMN IF NOT EXISTS cancel_reason TEXT");
    }

    private void migrateProducts() {
        execute("ALTER TABLE products ADD COLUMN IF NOT EXISTS option_code VARCHAR(100)");
        execute("ALTER TABLE products ADD COLUMN IF NOT EXISTS option_name VARCHAR(255)");
        execute("ALTER TABLE products ADD COLUMN IF NOT EXISTS vendor_name VARCHAR(100)");
        execute("ALTER TABLE products ADD COLUMN IF NOT EXISTS note VARCHAR(500)");
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
