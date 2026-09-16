ALTER TABLE daily_plans
    ADD CONSTRAINT uk_daily_plans_id_user UNIQUE (id, user_id);

ALTER TABLE daily_plan_items
    ADD CONSTRAINT uk_daily_plan_items_id_version
        UNIQUE (id, daily_plan_version_id);

CREATE TABLE task_guidances (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    owner_id UUID NOT NULL,
    daily_plan_id UUID NOT NULL,
    daily_plan_version_id UUID NOT NULL,
    daily_plan_item_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_task_guidances_item UNIQUE (daily_plan_item_id),
    CONSTRAINT fk_task_guidances_owner FOREIGN KEY (owner_id)
        REFERENCES user_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_guidances_plan_owner
        FOREIGN KEY (daily_plan_id, owner_id)
        REFERENCES daily_plans (id, user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_guidances_version_plan
        FOREIGN KEY (daily_plan_version_id, daily_plan_id)
        REFERENCES daily_plan_versions (id, daily_plan_id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_guidances_item_version
        FOREIGN KEY (daily_plan_item_id, daily_plan_version_id)
        REFERENCES daily_plan_items (id, daily_plan_version_id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_task_guidances_owner_item
    ON task_guidances (owner_id, daily_plan_item_id);

CREATE TABLE task_guidance_revisions (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    task_guidance_id UUID NOT NULL,
    ai_execution_id UUID NOT NULL,
    revision_number INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    objective VARCHAR(500) NOT NULL,
    task_summary TEXT NOT NULL,
    daily_plan_item_entity_version BIGINT NOT NULL,
    context_fingerprint VARCHAR(64) NOT NULL,
    draft_slot_guidance_id UUID,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_task_guidance_revisions_guidance
        FOREIGN KEY (task_guidance_id)
        REFERENCES task_guidances (id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_guidance_revisions_execution
        FOREIGN KEY (ai_execution_id)
        REFERENCES ai_executions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_guidance_revisions_draft_slot
        FOREIGN KEY (draft_slot_guidance_id)
        REFERENCES task_guidances (id) ON DELETE RESTRICT,
    CONSTRAINT uk_task_guidance_revisions_execution
        UNIQUE (ai_execution_id),
    CONSTRAINT uk_task_guidance_revisions_number
        UNIQUE (task_guidance_id, revision_number),
    CONSTRAINT uk_task_guidance_revisions_draft_slot
        UNIQUE (draft_slot_guidance_id),
    CONSTRAINT ck_task_guidance_revisions_number
        CHECK (revision_number > 0),
    CONSTRAINT ck_task_guidance_revisions_status
        CHECK (status IN ('DRAFT', 'SUPERSEDED', 'ARCHIVED')),
    CONSTRAINT ck_task_guidance_revisions_item_version
        CHECK (daily_plan_item_entity_version >= 0),
    CONSTRAINT ck_task_guidance_revisions_objective
        CHECK (char_length(trim(objective)) > 0),
    CONSTRAINT ck_task_guidance_revisions_summary
        CHECK (char_length(trim(task_summary)) > 0),
    CONSTRAINT ck_task_guidance_revisions_fingerprint
        CHECK (char_length(context_fingerprint) = 64),
    CONSTRAINT ck_task_guidance_revisions_draft_slot CHECK (
        (status = 'DRAFT' AND draft_slot_guidance_id = task_guidance_id)
        OR (status <> 'DRAFT' AND draft_slot_guidance_id IS NULL)
    )
);

CREATE INDEX idx_task_guidance_revisions_guidance_created
    ON task_guidance_revisions (task_guidance_id, revision_number DESC);

CREATE TABLE task_step_guidances (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    task_guidance_revision_id UUID NOT NULL,
    source_task_step_id UUID NOT NULL,
    task_step_entity_version BIGINT NOT NULL,
    order_index INTEGER NOT NULL,
    instructions TEXT NOT NULL,
    expected_result TEXT NOT NULL,
    tips TEXT,
    cautions TEXT,
    prerequisites TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_task_step_guidances_revision
        FOREIGN KEY (task_guidance_revision_id)
        REFERENCES task_guidance_revisions (id) ON DELETE RESTRICT,
    CONSTRAINT uk_task_step_guidances_id_revision
        UNIQUE (id, task_guidance_revision_id),
    CONSTRAINT uk_task_step_guidances_source
        UNIQUE (task_guidance_revision_id, source_task_step_id),
    CONSTRAINT uk_task_step_guidances_order
        UNIQUE (task_guidance_revision_id, order_index),
    CONSTRAINT ck_task_step_guidances_step_version
        CHECK (task_step_entity_version >= 0),
    CONSTRAINT ck_task_step_guidances_order
        CHECK (order_index >= 0),
    CONSTRAINT ck_task_step_guidances_instructions
        CHECK (char_length(trim(instructions)) > 0),
    CONSTRAINT ck_task_step_guidances_expected_result
        CHECK (char_length(trim(expected_result)) > 0)
);

CREATE INDEX idx_task_step_guidances_revision
    ON task_step_guidances (task_guidance_revision_id, order_index);

CREATE TABLE task_guidance_references (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    task_guidance_revision_id UUID NOT NULL,
    task_step_guidance_id UUID,
    provenance VARCHAR(30) NOT NULL,
    display_label VARCHAR(500) NOT NULL,
    locator VARCHAR(1000),
    material_id UUID,
    learning_source_id UUID,
    roadmap_item_id UUID,
    external_url VARCHAR(2048),
    order_index INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_task_guidance_references_revision
        FOREIGN KEY (task_guidance_revision_id)
        REFERENCES task_guidance_revisions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_guidance_references_step_revision
        FOREIGN KEY (task_step_guidance_id, task_guidance_revision_id)
        REFERENCES task_step_guidances (id, task_guidance_revision_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_task_guidance_references_material
        FOREIGN KEY (material_id)
        REFERENCES materials (id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_guidance_references_learning_source
        FOREIGN KEY (learning_source_id)
        REFERENCES learning_sources (id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_guidance_references_roadmap_item
        FOREIGN KEY (roadmap_item_id)
        REFERENCES roadmap_items (id) ON DELETE RESTRICT,
    CONSTRAINT ck_task_guidance_references_provenance CHECK (
        provenance IN (
            'MATERIAL',
            'LEARNING_SOURCE',
            'ROADMAP_CONTEXT',
            'UNVERIFIED_EXTERNAL'
        )
    ),
    CONSTRAINT ck_task_guidance_references_label
        CHECK (char_length(trim(display_label)) > 0),
    CONSTRAINT ck_task_guidance_references_order
        CHECK (order_index >= 0),
    CONSTRAINT uk_task_guidance_references_scope_order
        UNIQUE (
            task_guidance_revision_id,
            task_step_guidance_id,
            order_index
        ),
    CONSTRAINT ck_task_guidance_references_target CHECK (
        (
            provenance = 'MATERIAL'
            AND material_id IS NOT NULL
            AND learning_source_id IS NULL
            AND roadmap_item_id IS NULL
            AND external_url IS NULL
        )
        OR
        (
            provenance = 'LEARNING_SOURCE'
            AND material_id IS NULL
            AND learning_source_id IS NOT NULL
            AND roadmap_item_id IS NULL
            AND external_url IS NULL
        )
        OR
        (
            provenance = 'ROADMAP_CONTEXT'
            AND material_id IS NULL
            AND learning_source_id IS NULL
            AND roadmap_item_id IS NOT NULL
            AND external_url IS NULL
        )
        OR
        (
            provenance = 'UNVERIFIED_EXTERNAL'
            AND material_id IS NULL
            AND learning_source_id IS NULL
            AND roadmap_item_id IS NULL
            AND external_url IS NOT NULL
        )
    )
);

CREATE INDEX idx_task_guidance_references_revision
    ON task_guidance_references (task_guidance_revision_id);

ALTER TABLE ai_provider_configs
    DROP CONSTRAINT ck_ai_provider_config_purpose;

ALTER TABLE ai_provider_configs
    ADD CONSTRAINT ck_ai_provider_config_purpose CHECK (
        purpose IN (
            'DOCUMENT_EXTRACTION',
            'ROADMAP_GENERATION',
            'DAILY_PLAN_GENERATION',
            'DAILY_PLAN_REVIEW',
            'QUIZ_GENERATION',
            'TASK_GUIDANCE_GENERATION'
        )
    );

ALTER TABLE ai_executions
    DROP CONSTRAINT ck_ai_execution_target_result_match;

ALTER TABLE ai_executions
    DROP CONSTRAINT ck_ai_execution_purpose;

ALTER TABLE ai_executions
    ADD CONSTRAINT ck_ai_execution_purpose CHECK (
        purpose IN (
            'DOCUMENT_EXTRACTION',
            'ROADMAP_GENERATION',
            'DAILY_PLAN_GENERATION',
            'DAILY_PLAN_REVIEW',
            'QUIZ_GENERATION',
            'TASK_GUIDANCE_GENERATION'
        )
    );

ALTER TABLE ai_executions
    DROP CONSTRAINT ck_ai_execution_target_type;

ALTER TABLE ai_executions
    ADD CONSTRAINT ck_ai_execution_target_type CHECK (
        target_type IN (
            'ROADMAP',
            'DAILY_PLAN',
            'DAILY_PLAN_VERSION',
            'WEAK_TOPIC',
            'DAILY_PLAN_ITEM'
        )
    );

ALTER TABLE ai_executions
    DROP CONSTRAINT ck_ai_execution_result_type;

ALTER TABLE ai_executions
    ADD CONSTRAINT ck_ai_execution_result_type CHECK (
        result_type IS NULL
        OR result_type IN (
            'ROADMAP_VERSION',
            'DAILY_PLAN_VERSION',
            'QUIZ',
            'TASK_GUIDANCE_REVISION'
        )
    );

ALTER TABLE ai_executions
    ADD CONSTRAINT ck_ai_execution_target_result_match CHECK (
        result_type IS NULL
        OR (target_type = 'ROADMAP' AND result_type = 'ROADMAP_VERSION')
        OR (target_type = 'DAILY_PLAN' AND result_type = 'DAILY_PLAN_VERSION')
        OR (
            target_type IN ('DAILY_PLAN_VERSION', 'WEAK_TOPIC')
            AND result_type = 'QUIZ'
        )
        OR (
            target_type = 'DAILY_PLAN_ITEM'
            AND result_type = 'TASK_GUIDANCE_REVISION'
        )
    );
