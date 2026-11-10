CREATE TABLE ai_credit_rates (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    purpose VARCHAR(50) NOT NULL,
    model_category VARCHAR(50),
    credit_cost BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    effective_from TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_ai_credit_rates_cost CHECK (credit_cost > 0),
    CONSTRAINT ck_ai_credit_rates_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE TABLE ai_credit_reservations (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    wallet_id UUID NOT NULL,
    user_id UUID NOT NULL,
    ai_execution_id UUID NOT NULL,
    rate_id UUID,
    purpose VARCHAR(50) NOT NULL,
    model_category VARCHAR(50),
    reserved_credits BIGINT NOT NULL,
    charged_credits BIGINT,
    status VARCHAR(30) NOT NULL DEFAULT 'RESERVED',
    reserved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    settled_at TIMESTAMP WITH TIME ZONE,
    released_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_credit_reservations_wallet
        FOREIGN KEY (wallet_id) REFERENCES credit_wallets (id) ON DELETE CASCADE,
    CONSTRAINT fk_credit_reservations_user
        FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT fk_credit_reservations_execution
        FOREIGN KEY (ai_execution_id) REFERENCES ai_executions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_credit_reservations_rate
        FOREIGN KEY (rate_id) REFERENCES ai_credit_rates (id) ON DELETE SET NULL,
    CONSTRAINT uk_credit_reservations_execution UNIQUE (ai_execution_id),
    CONSTRAINT ck_credit_reservations_reserved CHECK (reserved_credits > 0),
    CONSTRAINT ck_credit_reservations_charged CHECK (charged_credits IS NULL OR charged_credits >= 0),
    CONSTRAINT ck_credit_reservations_status CHECK (status IN ('RESERVED', 'SETTLED', 'RELEASED'))
);

-- Seed initial default rates for AI purposes
INSERT INTO ai_credit_rates (id, version, purpose, model_category, credit_cost, status, effective_from, created_at, updated_at)
VALUES
    (gen_random_uuid(), 0, 'ROADMAP_GENERATION', NULL, 10, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (gen_random_uuid(), 0, 'DAILY_PLAN_GENERATION', NULL, 5, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (gen_random_uuid(), 0, 'DAILY_PLAN_REVIEW', NULL, 5, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (gen_random_uuid(), 0, 'QUIZ_GENERATION', NULL, 5, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (gen_random_uuid(), 0, 'TASK_GUIDANCE_GENERATION', NULL, 3, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (gen_random_uuid(), 0, 'DOCUMENT_EXTRACTION', NULL, 5, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
