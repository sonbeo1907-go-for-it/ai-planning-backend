-- V28__create_billing_credit_packages_and_top_up_orders.sql
-- Credit packages and top-up orders for AI credit purchasing

CREATE TABLE credit_packages (
    id UUID PRIMARY KEY,
    package_code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    price_vnd BIGINT NOT NULL,
    base_credits BIGINT NOT NULL,
    bonus_credits BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_credit_packages_code UNIQUE (package_code),
    CONSTRAINT ck_credit_packages_price_vnd CHECK (price_vnd > 0),
    CONSTRAINT ck_credit_packages_base_credits CHECK (base_credits > 0),
    CONSTRAINT ck_credit_packages_bonus_credits CHECK (bonus_credits >= 0),
    CONSTRAINT ck_credit_packages_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE INDEX idx_credit_packages_status_sort ON credit_packages (status, sort_order ASC);

CREATE TABLE top_up_orders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT,
    order_code VARCHAR(64) NOT NULL,
    package_id UUID REFERENCES credit_packages(id) ON DELETE SET NULL,
    package_code_snapshot VARCHAR(50) NOT NULL,
    package_name_snapshot VARCHAR(100) NOT NULL,
    price_vnd_snapshot BIGINT NOT NULL,
    base_credits_snapshot BIGINT NOT NULL,
    bonus_credits_snapshot BIGINT NOT NULL,
    total_credits_snapshot BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    payment_provider VARCHAR(50) NOT NULL,
    checkout_reference VARCHAR(255),
    external_transaction_id VARCHAR(255),
    idempotency_key VARCHAR(128) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    paid_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_top_up_orders_order_code UNIQUE (order_code),
    CONSTRAINT uk_top_up_orders_user_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT uk_top_up_orders_external_tx UNIQUE (external_transaction_id),
    CONSTRAINT ck_top_up_orders_price_snapshot CHECK (price_vnd_snapshot > 0),
    CONSTRAINT ck_top_up_orders_base_credits_snapshot CHECK (base_credits_snapshot > 0),
    CONSTRAINT ck_top_up_orders_bonus_credits_snapshot CHECK (bonus_credits_snapshot >= 0),
    CONSTRAINT ck_top_up_orders_total_credits_snapshot CHECK (total_credits_snapshot > 0),
    CONSTRAINT ck_top_up_orders_status CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_top_up_orders_currency CHECK (currency = 'VND')
);

CREATE INDEX idx_top_up_orders_user_created ON top_up_orders (user_id, created_at DESC);
CREATE INDEX idx_top_up_orders_status ON top_up_orders (status);

-- Seed initial packages
INSERT INTO credit_packages (
    id, package_code, name, price_vnd, base_credits, bonus_credits, status, sort_order, version, created_at, updated_at
) VALUES
    ('b1111111-1111-1111-1111-111111111111', 'AI_STARTER_50K', 'Gói AI Khởi Đầu', 50000, 5000, 0, 'ACTIVE', 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('b2222222-2222-2222-2222-222222222222', 'AI_PRO_100K', 'Gói AI Chuyên Nghiệp', 100000, 10000, 1000, 'ACTIVE', 2, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('b3333333-3333-3333-3333-333333333333', 'AI_ADVANCED_200K', 'Gói AI Nâng Cao', 200000, 20000, 3000, 'ACTIVE', 3, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('b4444444-4444-4444-4444-444444444444', 'AI_MASTER_500K', 'Gói AI Siêu Cấp', 500000, 50000, 10000, 'ACTIVE', 4, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('b5555555-5555-5555-5555-555555555555', 'AI_LEGACY_ARCHIVED', 'Gói Cũ Hết Hạn', 30000, 3000, 0, 'ARCHIVED', 99, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
