ALTER TABLE daily_plan_versions
    ADD COLUMN ai_explanation TEXT;

ALTER TABLE daily_plan_versions
    ADD COLUMN requires_user_decision BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE daily_plan_versions
    ADD COLUMN generation_request_key VARCHAR(100);

ALTER TABLE daily_plan_versions
    ADD CONSTRAINT uk_daily_plan_generation_request
        UNIQUE (daily_plan_id, generation_request_key);

ALTER TABLE daily_plan_items
    ADD COLUMN ai_adjustment_action VARCHAR(30);

ALTER TABLE daily_plan_items
    ADD COLUMN ai_adjustment_reason TEXT;

ALTER TABLE daily_plan_items
    ADD CONSTRAINT ck_daily_plan_items_ai_adjustment CHECK (
        (
            ai_adjustment_action IS NULL
            AND ai_adjustment_reason IS NULL
        )
        OR
        (
            ai_adjustment_action IN ('CARRY_OVER', 'SPLIT')
            AND ai_adjustment_reason IS NOT NULL
        )
    );
