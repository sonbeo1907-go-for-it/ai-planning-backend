CREATE TABLE daily_plans (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    user_id UUID NOT NULL,
    plan_date DATE NOT NULL,
    time_zone_snapshot VARCHAR(50) NOT NULL DEFAULT 'UTC',
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    active_version_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_daily_plans_user_date UNIQUE (user_id, plan_date),
    CONSTRAINT fk_daily_plans_user
        FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT ck_daily_plans_status CHECK (status IN ('DRAFT', 'READY', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX idx_daily_plans_user_date ON daily_plans (user_id, plan_date);

CREATE TABLE daily_plan_versions (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    daily_plan_id UUID NOT NULL,
    version_number INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    origin VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
    available_minutes INTEGER NOT NULL DEFAULT 60,
    total_planned_minutes INTEGER NOT NULL DEFAULT 0,
    draft_slot_daily_plan_id UUID,
    active_slot_daily_plan_id UUID,
    activated_at TIMESTAMP WITH TIME ZONE,
    superseded_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_daily_plan_versions UNIQUE (daily_plan_id, version_number),
    CONSTRAINT uk_daily_plan_versions_draft_slot UNIQUE (draft_slot_daily_plan_id),
    CONSTRAINT uk_daily_plan_versions_active_slot UNIQUE (active_slot_daily_plan_id),
    CONSTRAINT fk_daily_plan_versions_plan
        FOREIGN KEY (daily_plan_id) REFERENCES daily_plans (id) ON DELETE CASCADE,
    CONSTRAINT ck_daily_plan_versions_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'SUPERSEDED')),
    CONSTRAINT ck_daily_plan_versions_origin CHECK (origin IN ('MANUAL', 'AI_GENERATED', 'AI_REGENERATED', 'USER_EDITED')),
    CONSTRAINT ck_daily_plan_versions_available_minutes CHECK (available_minutes > 0),
    CONSTRAINT ck_daily_plan_versions_draft_slot CHECK (
        (status = 'DRAFT' AND draft_slot_daily_plan_id = daily_plan_id)
        OR (status <> 'DRAFT' AND draft_slot_daily_plan_id IS NULL)
    ),
    CONSTRAINT ck_daily_plan_versions_active_slot CHECK (
        (status = 'ACTIVE' AND active_slot_daily_plan_id = daily_plan_id)
        OR (status <> 'ACTIVE' AND active_slot_daily_plan_id IS NULL)
    )
);

CREATE INDEX idx_daily_plan_versions_plan ON daily_plan_versions (daily_plan_id);
CREATE INDEX idx_daily_plan_versions_plan_status
    ON daily_plan_versions (daily_plan_id, status);

ALTER TABLE daily_plans
    ADD CONSTRAINT fk_daily_plans_active_version
    FOREIGN KEY (active_version_id) REFERENCES daily_plan_versions (id);

CREATE TABLE daily_plan_items (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    daily_plan_version_id UUID NOT NULL,
    category VARCHAR(30) NOT NULL DEFAULT 'CUSTOM',
    title VARCHAR(255) NOT NULL,
    description TEXT,
    planned_minutes INTEGER NOT NULL,
    order_index INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'NOT_STARTED',
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_daily_plan_items_version
        FOREIGN KEY (daily_plan_version_id) REFERENCES daily_plan_versions (id) ON DELETE CASCADE,
    CONSTRAINT ck_daily_plan_items_category CHECK (category IN ('REVIEW', 'NEW_MATERIAL', 'PRACTICE', 'CUSTOM')),
    CONSTRAINT ck_daily_plan_items_status CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'PARTIALLY_COMPLETED', 'SKIPPED')),
    CONSTRAINT ck_daily_plan_items_planned_minutes CHECK (planned_minutes > 0),
    CONSTRAINT ck_daily_plan_items_order_index CHECK (order_index >= 0)
);

CREATE INDEX idx_daily_plan_items_version ON daily_plan_items (daily_plan_version_id);

CREATE TABLE progress_entries (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    daily_plan_item_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    actual_minutes INTEGER NOT NULL DEFAULT 0,
    completion_percentage INTEGER NOT NULL DEFAULT 0,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_progress_entries_user
        FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT fk_progress_entries_item
        FOREIGN KEY (daily_plan_item_id) REFERENCES daily_plan_items (id) ON DELETE RESTRICT,
    CONSTRAINT ck_progress_entries_percentage CHECK (completion_percentage BETWEEN 0 AND 100)
);

CREATE INDEX idx_progress_entries_user ON progress_entries (user_id);
CREATE INDEX idx_progress_entries_item ON progress_entries (daily_plan_item_id);
