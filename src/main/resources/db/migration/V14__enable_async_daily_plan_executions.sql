ALTER TABLE ai_executions DROP CONSTRAINT ck_ai_execution_target_type;
ALTER TABLE ai_executions
    ADD CONSTRAINT ck_ai_execution_target_type
        CHECK (target_type IN ('ROADMAP', 'DAILY_PLAN'));

ALTER TABLE ai_executions DROP CONSTRAINT ck_ai_execution_result_type;
ALTER TABLE ai_executions
    ADD CONSTRAINT ck_ai_execution_result_type
        CHECK (
            result_type IS NULL
            OR result_type IN ('ROADMAP_VERSION', 'DAILY_PLAN_VERSION')
        );

ALTER TABLE ai_executions
    ADD CONSTRAINT ck_ai_execution_target_result_match
        CHECK (
            result_type IS NULL
            OR (target_type = 'ROADMAP' AND result_type = 'ROADMAP_VERSION')
            OR (target_type = 'DAILY_PLAN' AND result_type = 'DAILY_PLAN_VERSION')
        );

CREATE INDEX idx_daily_plans_owner_status_date
    ON daily_plans (user_id, status, plan_date DESC);
