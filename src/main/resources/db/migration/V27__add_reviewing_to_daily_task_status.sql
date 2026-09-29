ALTER TABLE daily_plan_items
    DROP CONSTRAINT IF EXISTS ck_daily_plan_items_status;

ALTER TABLE daily_plan_items
    ADD CONSTRAINT ck_daily_plan_items_status
    CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'REVIEWING', 'COMPLETED', 'PARTIALLY_COMPLETED', 'SKIPPED'));
