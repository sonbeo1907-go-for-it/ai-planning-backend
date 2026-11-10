ALTER TABLE roadmap_items DROP CONSTRAINT ck_roadmap_items_type;
ALTER TABLE roadmap_items DROP CONSTRAINT ck_roadmap_items_shape;
ALTER TABLE roadmap_items DROP CONSTRAINT fk_roadmap_items_parent;

ALTER TABLE roadmap_versions ADD COLUMN source_roadmap_version_id UUID;

ALTER TABLE roadmap_versions ADD CONSTRAINT fk_roadmap_versions_source
    FOREIGN KEY (source_roadmap_version_id)
    REFERENCES roadmap_versions (id) ON DELETE RESTRICT;

ALTER TABLE roadmap_versions ADD CONSTRAINT ck_roadmap_versions_source_not_self
    CHECK (source_roadmap_version_id IS NULL OR source_roadmap_version_id <> id);

CREATE INDEX idx_roadmap_versions_source
    ON roadmap_versions (source_roadmap_version_id);

ALTER TABLE roadmap_items ADD CONSTRAINT ck_roadmap_items_type
    CHECK (item_type IN ('MILESTONE', 'TOPIC', 'LEARNING_UNIT'));

ALTER TABLE roadmap_items ADD COLUMN parent_item_type VARCHAR(30);

UPDATE roadmap_items child
SET parent_item_type = (
    SELECT parent.item_type
    FROM roadmap_items parent
    WHERE parent.id = child.parent_item_id
)
WHERE child.parent_item_id IS NOT NULL;

ALTER TABLE roadmap_items ADD CONSTRAINT uk_roadmap_items_id_version_type
    UNIQUE (id, roadmap_version_id, item_type);

ALTER TABLE roadmap_items ADD CONSTRAINT fk_roadmap_items_parent_hierarchy
    FOREIGN KEY (parent_item_id, roadmap_version_id, parent_item_type)
    REFERENCES roadmap_items (id, roadmap_version_id, item_type) ON DELETE CASCADE;

ALTER TABLE roadmap_items ADD CONSTRAINT ck_roadmap_items_shape CHECK (
    (item_type = 'MILESTONE'
        AND parent_item_id IS NULL
        AND parent_item_type IS NULL
        AND estimated_minutes IS NULL)
    OR
    (item_type = 'TOPIC'
        AND parent_item_id IS NOT NULL
        AND parent_item_type IS NOT NULL
        AND parent_item_type = 'MILESTONE'
        AND estimated_minutes > 0)
    OR
    (item_type = 'LEARNING_UNIT'
        AND parent_item_id IS NOT NULL
        AND parent_item_type IS NOT NULL
        AND parent_item_type = 'TOPIC'
        AND estimated_minutes > 0)
);

ALTER TABLE roadmap_items ADD COLUMN lineage_id UUID;

-- Preserve pre-V16 Roadmaps with one explicit fallback unit per Topic. New and
-- edited Roadmaps must still define their real, atomic Learning Units in the API.
INSERT INTO roadmap_items (
    id,
    version,
    roadmap_version_id,
    parent_item_id,
    parent_item_type,
    item_type,
    title,
    description,
    order_index,
    estimated_minutes,
    lineage_id,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid(),
    0,
    topic.roadmap_version_id,
    topic.id,
    'TOPIC',
    'LEARNING_UNIT',
    topic.title,
    topic.description,
    0,
    topic.estimated_minutes,
    gen_random_uuid(),
    topic.created_at,
    topic.updated_at
FROM roadmap_items topic
WHERE topic.item_type = 'TOPIC';

ALTER TABLE roadmap_items ADD CONSTRAINT ck_roadmap_items_learning_unit_lineage CHECK (
    (item_type = 'LEARNING_UNIT' AND lineage_id IS NOT NULL)
    OR (item_type <> 'LEARNING_UNIT' AND lineage_id IS NULL)
);

ALTER TABLE roadmap_items ADD CONSTRAINT uk_roadmap_items_version_lineage
    UNIQUE (roadmap_version_id, lineage_id);

CREATE INDEX idx_roadmap_items_version_type_parent_order
    ON roadmap_items (roadmap_version_id, item_type, parent_item_id, order_index);

UPDATE daily_plan_items task
SET roadmap_item_id = (
    SELECT unit.id
    FROM roadmap_items unit
    WHERE unit.parent_item_id = task.roadmap_item_id
      AND unit.item_type = 'LEARNING_UNIT'
)
WHERE EXISTS (
    SELECT 1
    FROM roadmap_items unit
    WHERE unit.parent_item_id = task.roadmap_item_id
      AND unit.item_type = 'LEARNING_UNIT'
);

ALTER TABLE daily_plan_items
    ADD CONSTRAINT fk_daily_plan_items_roadmap_item
    FOREIGN KEY (roadmap_item_id) REFERENCES roadmap_items (id) ON DELETE RESTRICT;

CREATE INDEX idx_daily_plan_items_roadmap_item
    ON daily_plan_items (roadmap_item_id);

ALTER TABLE daily_plan_items ADD COLUMN removed_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_daily_plan_items_visible_order
    ON daily_plan_items (daily_plan_version_id, removed_at, order_index);

ALTER TABLE progress_entries ADD COLUMN roadmap_version_id UUID;
ALTER TABLE progress_entries ADD COLUMN learning_unit_id UUID;
ALTER TABLE progress_entries ADD COLUMN learning_unit_type VARCHAR(30)
    NOT NULL DEFAULT 'LEARNING_UNIT';
ALTER TABLE progress_entries ADD COLUMN idempotency_key VARCHAR(100);

UPDATE progress_entries progress
SET roadmap_version_id = (
        SELECT unit.roadmap_version_id
        FROM daily_plan_items task
        JOIN roadmap_items unit ON unit.id = task.roadmap_item_id
        WHERE task.id = progress.daily_plan_item_id
          AND unit.item_type = 'LEARNING_UNIT'
    ),
    learning_unit_id = (
        SELECT unit.id
        FROM daily_plan_items task
        JOIN roadmap_items unit ON unit.id = task.roadmap_item_id
        WHERE task.id = progress.daily_plan_item_id
          AND unit.item_type = 'LEARNING_UNIT'
    )
WHERE EXISTS (
    SELECT 1
    FROM daily_plan_items task
    JOIN roadmap_items unit ON unit.id = task.roadmap_item_id
    WHERE task.id = progress.daily_plan_item_id
      AND unit.item_type = 'LEARNING_UNIT'
);

ALTER TABLE progress_entries DROP CONSTRAINT fk_progress_entries_item;
ALTER TABLE progress_entries ALTER COLUMN daily_plan_item_id DROP NOT NULL;
ALTER TABLE progress_entries ADD CONSTRAINT fk_progress_entries_item
    FOREIGN KEY (daily_plan_item_id)
    REFERENCES daily_plan_items (id) ON DELETE SET NULL;

ALTER TABLE progress_entries ADD CONSTRAINT fk_progress_entries_learning_unit_version
    FOREIGN KEY (learning_unit_id, roadmap_version_id, learning_unit_type)
    REFERENCES roadmap_items (id, roadmap_version_id, item_type) ON DELETE RESTRICT;

ALTER TABLE progress_entries ADD CONSTRAINT ck_progress_entries_learning_unit_type
    CHECK (learning_unit_type = 'LEARNING_UNIT');

ALTER TABLE progress_entries ADD CONSTRAINT ck_progress_entries_learning_target CHECK (
    (learning_unit_id IS NULL AND roadmap_version_id IS NULL)
    OR (learning_unit_id IS NOT NULL AND roadmap_version_id IS NOT NULL)
);

ALTER TABLE progress_entries ADD CONSTRAINT uk_progress_entries_owner_idempotency
    UNIQUE (user_id, idempotency_key);

CREATE INDEX idx_progress_entries_owner_learning_unit_recorded
    ON progress_entries (user_id, learning_unit_id, recorded_at DESC, id DESC);

ALTER TABLE weak_topics DROP CONSTRAINT fk_weak_topics_item_version;
ALTER TABLE weak_topics ADD COLUMN roadmap_item_type VARCHAR(30)
    NOT NULL DEFAULT 'LEARNING_UNIT';

UPDATE weak_topics weak
SET roadmap_item_id = (
    SELECT unit.id
    FROM roadmap_items unit
    WHERE unit.parent_item_id = weak.roadmap_item_id
      AND unit.item_type = 'LEARNING_UNIT'
)
WHERE EXISTS (
    SELECT 1
    FROM roadmap_items unit
    WHERE unit.parent_item_id = weak.roadmap_item_id
      AND unit.item_type = 'LEARNING_UNIT'
);

ALTER TABLE weak_topics ADD CONSTRAINT fk_weak_topics_learning_unit_version
    FOREIGN KEY (roadmap_item_id, roadmap_version_id, roadmap_item_type)
    REFERENCES roadmap_items (id, roadmap_version_id, item_type) ON DELETE RESTRICT;

ALTER TABLE weak_topics ADD CONSTRAINT ck_weak_topics_learning_unit_type
    CHECK (roadmap_item_type = 'LEARNING_UNIT');

CREATE TABLE roadmap_item_progress (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    user_id UUID NOT NULL,
    roadmap_version_id UUID NOT NULL,
    roadmap_item_id UUID NOT NULL,
    roadmap_item_type VARCHAR(30) NOT NULL DEFAULT 'LEARNING_UNIT',
    status VARCHAR(30) NOT NULL DEFAULT 'NOT_STARTED',
    latest_outcome VARCHAR(30),
    completion_percentage INTEGER NOT NULL DEFAULT 0,
    last_progress_entry_id UUID,
    carried_from_progress_id UUID,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_roadmap_item_progress_user
        FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_roadmap_item_progress_item_version
        FOREIGN KEY (roadmap_item_id, roadmap_version_id, roadmap_item_type)
        REFERENCES roadmap_items (id, roadmap_version_id, item_type) ON DELETE RESTRICT,
    CONSTRAINT fk_roadmap_item_progress_entry
        FOREIGN KEY (last_progress_entry_id)
        REFERENCES progress_entries (id) ON DELETE SET NULL,
    CONSTRAINT fk_roadmap_item_progress_carried_from
        FOREIGN KEY (carried_from_progress_id)
        REFERENCES roadmap_item_progress (id) ON DELETE RESTRICT,
    CONSTRAINT uk_roadmap_item_progress_user_item
        UNIQUE (user_id, roadmap_item_id),
    CONSTRAINT ck_roadmap_item_progress_status
        CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT ck_roadmap_item_progress_item_type
        CHECK (roadmap_item_type = 'LEARNING_UNIT'),
    CONSTRAINT ck_roadmap_item_progress_latest_outcome
        CHECK (latest_outcome IS NULL OR latest_outcome IN (
            'COMPLETED', 'PARTIALLY_COMPLETED', 'SKIPPED'
        )),
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
