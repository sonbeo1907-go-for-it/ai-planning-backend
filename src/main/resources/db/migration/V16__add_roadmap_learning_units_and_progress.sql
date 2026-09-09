ALTER TABLE roadmap_items DROP CONSTRAINT ck_roadmap_items_type;
ALTER TABLE roadmap_items DROP CONSTRAINT ck_roadmap_items_shape;

ALTER TABLE roadmap_items ADD CONSTRAINT ck_roadmap_items_type
    CHECK (item_type IN ('MILESTONE', 'TOPIC', 'LEARNING_UNIT'));

ALTER TABLE roadmap_items ADD CONSTRAINT ck_roadmap_items_shape CHECK (
    (item_type = 'MILESTONE'
        AND parent_item_id IS NULL
        AND estimated_minutes IS NULL)
    OR
    (item_type IN ('TOPIC', 'LEARNING_UNIT')
        AND parent_item_id IS NOT NULL
        AND estimated_minutes > 0)
);

CREATE INDEX idx_roadmap_items_version_type_parent_order
    ON roadmap_items (roadmap_version_id, item_type, parent_item_id, order_index);

ALTER TABLE daily_plan_items
    ADD CONSTRAINT fk_daily_plan_items_roadmap_item
    FOREIGN KEY (roadmap_item_id) REFERENCES roadmap_items (id) ON DELETE RESTRICT;

CREATE INDEX idx_daily_plan_items_roadmap_item
    ON daily_plan_items (roadmap_item_id);

CREATE TABLE roadmap_item_progress (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    user_id UUID NOT NULL,
    roadmap_version_id UUID NOT NULL,
    roadmap_item_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'NOT_STARTED',
    completion_percentage INTEGER NOT NULL DEFAULT 0,
    last_progress_entry_id UUID,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_roadmap_item_progress_user
        FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_roadmap_item_progress_item_version
        FOREIGN KEY (roadmap_item_id, roadmap_version_id)
        REFERENCES roadmap_items (id, roadmap_version_id) ON DELETE RESTRICT,
    CONSTRAINT fk_roadmap_item_progress_entry
        FOREIGN KEY (last_progress_entry_id)
        REFERENCES progress_entries (id) ON DELETE SET NULL,
    CONSTRAINT uk_roadmap_item_progress_user_item
        UNIQUE (user_id, roadmap_item_id),
    CONSTRAINT ck_roadmap_item_progress_status
        CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT ck_roadmap_item_progress_percentage
        CHECK (completion_percentage BETWEEN 0 AND 100),
    CONSTRAINT ck_roadmap_item_progress_completion CHECK (
        (status = 'COMPLETED'
            AND completion_percentage = 100
            AND completed_at IS NOT NULL)
        OR
        (status <> 'COMPLETED'
            AND completion_percentage < 100
            AND completed_at IS NULL)
    )
);

CREATE INDEX idx_roadmap_item_progress_owner_version_status
    ON roadmap_item_progress (user_id, roadmap_version_id, status);
