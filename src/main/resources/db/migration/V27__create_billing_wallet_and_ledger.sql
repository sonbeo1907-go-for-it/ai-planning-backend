CREATE TABLE credit_wallets (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    user_id UUID NOT NULL,
    available_credits BIGINT NOT NULL DEFAULT 0,
    reserved_credits BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_credit_wallets_user FOREIGN KEY (user_id)
        REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT uk_credit_wallets_user UNIQUE (user_id),
    CONSTRAINT ck_credit_wallets_available_credits CHECK (available_credits >= 0),
    CONSTRAINT ck_credit_wallets_reserved_credits CHECK (reserved_credits >= 0)
);

CREATE TABLE credit_ledger_entries (
    id UUID PRIMARY KEY,
    wallet_id UUID NOT NULL,
    user_id UUID NOT NULL,
    entry_type VARCHAR(50) NOT NULL,
    available_delta BIGINT NOT NULL DEFAULT 0,
    reserved_delta BIGINT NOT NULL DEFAULT 0,
    available_balance_after BIGINT NOT NULL,
    reserved_balance_after BIGINT NOT NULL,
    reference_type VARCHAR(50) NOT NULL,
    reference_id UUID,
    idempotency_key VARCHAR(150),
    description VARCHAR(255),
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_credit_ledger_entries_wallet FOREIGN KEY (wallet_id)
        REFERENCES credit_wallets (id) ON DELETE RESTRICT,
    CONSTRAINT fk_credit_ledger_entries_user FOREIGN KEY (user_id)
        REFERENCES user_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT uk_credit_ledger_entries_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_credit_ledger_entries_entry_type CHECK (
        entry_type IN ('WELCOME_BONUS', 'TOP_UP', 'RESERVE', 'USAGE', 'RELEASE_RESERVE', 'REFUND', 'ADJUSTMENT')
    ),
    CONSTRAINT ck_credit_ledger_entries_reference_type CHECK (
        reference_type IN ('ACCOUNT', 'ORDER', 'RESERVATION', 'AI_EXECUTION', 'ADJUSTMENT')
    )
);

CREATE INDEX idx_credit_ledger_entries_wallet_id ON credit_ledger_entries (wallet_id);
CREATE INDEX idx_credit_ledger_entries_user_id ON credit_ledger_entries (user_id);
CREATE INDEX idx_credit_ledger_entries_recorded_at ON credit_ledger_entries (recorded_at);
