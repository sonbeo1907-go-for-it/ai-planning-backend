CREATE TABLE ai_executions (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    owner_id UUID NOT NULL,
    provider_config_id UUID NOT NULL,
    purpose VARCHAR(50) NOT NULL,
    operation VARCHAR(30) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id UUID NOT NULL,
    active_slot_target_id UUID,
    status VARCHAR(30) NOT NULL,
    result_type VARCHAR(40),
    result_id UUID,
    idempotency_key VARCHAR(100),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    failure_code VARCHAR(100),
    failure_message VARCHAR(500),
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    lease_expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_ai_executions_owner FOREIGN KEY (owner_id)
        REFERENCES user_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_executions_provider_config FOREIGN KEY (provider_config_id)
        REFERENCES ai_provider_configs (id) ON DELETE RESTRICT,
    CONSTRAINT uk_ai_execution_active_target
        UNIQUE (owner_id, purpose, target_type, active_slot_target_id),
    CONSTRAINT uk_ai_execution_idempotency
        UNIQUE (owner_id, idempotency_key),
    CONSTRAINT ck_ai_execution_purpose CHECK (
        purpose IN (
            'DOCUMENT_EXTRACTION',
            'ROADMAP_GENERATION',
            'DAILY_PLAN_GENERATION',
            'DAILY_PLAN_REVIEW'
        )
    ),
    CONSTRAINT ck_ai_execution_operation CHECK (
        operation IN ('GENERATE', 'REGENERATE')
    ),
    CONSTRAINT ck_ai_execution_target_type CHECK (target_type IN ('ROADMAP')),
    CONSTRAINT ck_ai_execution_status CHECK (
        status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED')
    ),
    CONSTRAINT ck_ai_execution_result_type CHECK (
        result_type IS NULL OR result_type IN ('ROADMAP_VERSION')
    ),
    CONSTRAINT ck_ai_execution_active_slot CHECK (
        (
            status IN ('QUEUED', 'RUNNING')
            AND active_slot_target_id = target_id
        )
        OR
        (
            status IN ('SUCCEEDED', 'FAILED')
            AND active_slot_target_id IS NULL
        )
    ),
    CONSTRAINT ck_ai_execution_result CHECK (
        (
            status = 'SUCCEEDED'
            AND result_type IS NOT NULL
            AND result_id IS NOT NULL
            AND failure_code IS NULL
            AND failure_message IS NULL
        )
        OR
        (
            status = 'FAILED'
            AND result_type IS NULL
            AND result_id IS NULL
            AND failure_code IS NOT NULL
            AND failure_message IS NOT NULL
        )
        OR status IN ('QUEUED', 'RUNNING')
    )
);

CREATE TABLE ai_execution_inputs (
    execution_id UUID PRIMARY KEY,
    adjustment_prompt VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_ai_execution_inputs_execution FOREIGN KEY (execution_id)
        REFERENCES ai_executions (id) ON DELETE CASCADE
);

CREATE INDEX idx_ai_executions_owner_created
    ON ai_executions (owner_id, created_at);

CREATE INDEX idx_ai_executions_dispatch
    ON ai_executions (status, created_at);

CREATE INDEX idx_ai_executions_target_history
    ON ai_executions (owner_id, target_type, target_id, purpose, created_at);
