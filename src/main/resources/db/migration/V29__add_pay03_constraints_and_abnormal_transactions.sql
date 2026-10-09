-- V29__add_pay03_constraints_and_abnormal_transactions.sql
-- US-PAY-03: Add database unique constraint for TOP_UP ledger entries and table for abnormal payment events

-- 1. Ensure at most one TOP_UP ledger entry per order using unique constraint on top_up_order_id
ALTER TABLE credit_ledger_entries ADD COLUMN top_up_order_id UUID;
ALTER TABLE credit_ledger_entries ADD CONSTRAINT uk_credit_ledger_top_up_order UNIQUE (top_up_order_id);

-- 2. Persistent table for abnormal payment events/transactions
CREATE TABLE billing_abnormal_transactions (
    id UUID PRIMARY KEY,
    order_code VARCHAR(64),
    external_transaction_id VARCHAR(255),
    amount_vnd BIGINT,
    reason VARCHAR(100) NOT NULL,
    details TEXT,
    raw_payload TEXT,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_billing_abnormal_order_code ON billing_abnormal_transactions (order_code);
CREATE INDEX idx_billing_abnormal_ext_tx ON billing_abnormal_transactions (external_transaction_id);
CREATE INDEX idx_billing_abnormal_recorded_at ON billing_abnormal_transactions (recorded_at);
