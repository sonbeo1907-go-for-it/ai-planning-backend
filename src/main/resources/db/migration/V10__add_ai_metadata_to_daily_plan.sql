ALTER TABLE daily_plan_versions ADD COLUMN ai_explanation TEXT;
ALTER TABLE daily_plan_versions ADD COLUMN requires_user_decision BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE daily_plan_items ADD COLUMN ai_adjustment_action VARCHAR(30);
ALTER TABLE daily_plan_items ADD COLUMN ai_adjustment_reason TEXT;
