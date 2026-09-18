ALTER TABLE ai_executions
    ADD COLUMN latency_ms BIGINT,
    ADD COLUMN input_tokens INTEGER,
    ADD COLUMN output_tokens INTEGER;

ALTER TABLE ai_executions
    DROP CONSTRAINT ck_ai_execution_status;

ALTER TABLE ai_executions
    ADD CONSTRAINT ck_ai_execution_status
        CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'TIMEOUT'));

ALTER TABLE ai_executions
    DROP CONSTRAINT ck_ai_execution_active_slot;

ALTER TABLE ai_executions
    ADD CONSTRAINT ck_ai_execution_active_slot
        CHECK (
            (
                status IN ('QUEUED', 'RUNNING')
                AND active_slot_target_id = target_id
            )
            OR
            (
                status IN ('SUCCEEDED', 'FAILED', 'TIMEOUT')
                AND active_slot_target_id IS NULL
            )
        );

ALTER TABLE ai_executions
    DROP CONSTRAINT ck_ai_execution_result;

ALTER TABLE ai_executions
    ADD CONSTRAINT ck_ai_execution_result
        CHECK (
            (
                status = 'SUCCEEDED'
                AND result_type IS NOT NULL
                AND result_id IS NOT NULL
                AND failure_code IS NULL
                AND failure_message IS NULL
            )
            OR
            (
                status IN ('FAILED', 'TIMEOUT')
                AND result_type IS NULL
                AND result_id IS NULL
                AND failure_code IS NOT NULL
                AND failure_message IS NOT NULL
            )
            OR status IN ('QUEUED', 'RUNNING')
        );

CREATE INDEX idx_ai_executions_started_at
    ON ai_executions (started_at);
