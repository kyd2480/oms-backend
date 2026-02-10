-- ============================================
-- Order Collector Database Schema
-- ============================================

-- 1. 판매처 (Sales Channels)
CREATE TABLE IF NOT EXISTS sales_channels (
    channel_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    channel_code VARCHAR(50) UNIQUE NOT NULL,
    channel_name VARCHAR(100) NOT NULL,
    api_type VARCHAR(20),
    api_base_url VARCHAR(255),
    credentials JSONB,
    is_active BOOLEAN DEFAULT true,
    collection_interval INTEGER DEFAULT 10,
    last_collected_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

COMMENT ON TABLE sales_channels IS '판매처 정보';
COMMENT ON COLUMN sales_channels.channel_code IS '판매처 코드 (NAVER, COUPANG, 11ST 등)';
COMMENT ON COLUMN sales_channels.credentials IS 'API 인증 정보 (JSON, 암호화 권장)';
COMMENT ON COLUMN sales_channels.collection_interval IS '수집 주기 (분)';

-- 2. 원본 주문 (Raw Orders)
CREATE TABLE IF NOT EXISTS raw_orders (
    raw_order_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    channel_id UUID REFERENCES sales_channels(channel_id) ON DELETE CASCADE,
    channel_order_no VARCHAR(100) NOT NULL,
    raw_data JSONB NOT NULL,
    collected_at TIMESTAMP DEFAULT NOW(),
    processed BOOLEAN DEFAULT false,
    processed_at TIMESTAMP,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    CONSTRAINT uk_raw_orders_channel_order UNIQUE (channel_id, channel_order_no)
);

COMMENT ON TABLE raw_orders IS '수집된 원본 주문 데이터';
COMMENT ON COLUMN raw_orders.raw_data IS '판매처에서 수집한 원본 JSON 데이터';
COMMENT ON COLUMN raw_orders.processed IS '정규화 처리 완료 여부';

CREATE INDEX idx_raw_orders_channel ON raw_orders(channel_id);
CREATE INDEX idx_raw_orders_processed ON raw_orders(processed, collected_at);
CREATE INDEX idx_raw_orders_collected_at ON raw_orders(collected_at DESC);

-- 3. 정규화된 주문 (Orders)
CREATE TABLE IF NOT EXISTS orders (
    order_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_no VARCHAR(100) UNIQUE NOT NULL,
    raw_order_id UUID REFERENCES raw_orders(raw_order_id),
    channel_id UUID REFERENCES sales_channels(channel_id),
    channel_order_no VARCHAR(100),
    
    -- 고객 정보
    customer_name VARCHAR(100) NOT NULL,
    customer_phone VARCHAR(20),
    customer_email VARCHAR(100),
    
    -- 배송 정보
    recipient_name VARCHAR(100) NOT NULL,
    recipient_phone VARCHAR(20) NOT NULL,
    postal_code VARCHAR(10),
    address VARCHAR(255) NOT NULL,
    address_detail VARCHAR(255),
    delivery_memo TEXT,
    
    -- 금액 정보
    total_amount DECIMAL(15,2) NOT NULL,
    payment_amount DECIMAL(15,2) NOT NULL,
    shipping_fee DECIMAL(10,2) DEFAULT 0,
    discount_amount DECIMAL(10,2) DEFAULT 0,
    
    -- 상태
    order_status VARCHAR(20) DEFAULT 'PENDING',
    payment_status VARCHAR(20) DEFAULT 'PENDING',
    
    -- 날짜
    ordered_at TIMESTAMP NOT NULL,
    paid_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

COMMENT ON TABLE orders IS '정규화된 주문 정보';
COMMENT ON COLUMN orders.order_no IS 'OMS 통합 주문번호 (OMS-YYYYMMDD-XXXX)';
COMMENT ON COLUMN orders.order_status IS '주문 상태: PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED';
COMMENT ON COLUMN orders.payment_status IS '결제 상태: PENDING, PAID, CANCELLED, REFUNDED';

CREATE INDEX idx_orders_channel ON orders(channel_id);
CREATE INDEX idx_orders_status ON orders(order_status);
CREATE INDEX idx_orders_date ON orders(ordered_at DESC);
CREATE INDEX idx_orders_customer ON orders(customer_phone, customer_name);

-- 4. 주문 상품 (Order Items)
CREATE TABLE IF NOT EXISTS order_items (
    item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID REFERENCES orders(order_id) ON DELETE CASCADE,
    
    -- 상품 정보
    product_code VARCHAR(100),
    channel_product_code VARCHAR(100),
    product_name VARCHAR(255) NOT NULL,
    option_name VARCHAR(255),
    
    -- 수량 및 가격
    quantity INTEGER NOT NULL,
    unit_price DECIMAL(15,2) NOT NULL,
    total_price DECIMAL(15,2) NOT NULL,
    
    created_at TIMESTAMP DEFAULT NOW()
);

COMMENT ON TABLE order_items IS '주문 상품 정보';
COMMENT ON COLUMN order_items.product_code IS '자사 상품 코드';
COMMENT ON COLUMN order_items.channel_product_code IS '판매처 상품 코드';

CREATE INDEX idx_order_items_order ON order_items(order_id);
CREATE INDEX idx_order_items_product ON order_items(product_code);

-- 5. 주문 수집 이력 (Collection History)
CREATE TABLE IF NOT EXISTS collection_history (
    history_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    channel_id UUID REFERENCES sales_channels(channel_id),
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP NOT NULL,
    collected_count INTEGER DEFAULT 0,
    success_count INTEGER DEFAULT 0,
    error_count INTEGER DEFAULT 0,
    status VARCHAR(20),
    error_message TEXT,
    started_at TIMESTAMP DEFAULT NOW(),
    completed_at TIMESTAMP
);

COMMENT ON TABLE collection_history IS '주문 수집 실행 이력';
CREATE INDEX idx_collection_history_channel ON collection_history(channel_id, started_at DESC);

-- 초기 데이터: 판매처 등록
INSERT INTO sales_channels (channel_code, channel_name, api_type, is_active) VALUES
('NAVER', '네이버 스마트스토어', 'REST', true),
('COUPANG', '쿠팡', 'REST', true),
('11ST', '11번가', 'CSV', true),
('GMARKET', 'G마켓', 'REST', false),
('AUCTION', '옥션', 'REST', false)
ON CONFLICT (channel_code) DO NOTHING;
