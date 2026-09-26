CREATE TABLE ai_prompt_templates (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    purpose VARCHAR(50) NOT NULL,
    version_number INTEGER NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT false,
    active_slot_purpose VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_ai_prompt_templates_purpose_version UNIQUE (purpose, version_number),
    CONSTRAINT uk_ai_prompt_templates_active UNIQUE (active_slot_purpose),
    CONSTRAINT ck_ai_prompt_template_purpose CHECK (
        purpose IN (
            'DOCUMENT_EXTRACTION',
            'ROADMAP_GENERATION',
            'DAILY_PLAN_GENERATION',
            'DAILY_PLAN_REVIEW',
            'QUIZ_GENERATION',
            'TASK_GUIDANCE_GENERATION'
        )
    ),
    CONSTRAINT ck_ai_prompt_template_status CHECK (
        status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')
    ),
    CONSTRAINT ck_ai_prompt_template_active_status CHECK (
        (is_active = true AND status = 'PUBLISHED')
        OR
        (is_active = false)
    ),
    CONSTRAINT ck_ai_prompt_template_active_slot CHECK (
        (is_active = true AND active_slot_purpose = purpose)
        OR
        (is_active = false AND active_slot_purpose IS NULL)
    )
);

CREATE INDEX idx_ai_prompt_templates_purpose_status
    ON ai_prompt_templates (purpose, status);

ALTER TABLE ai_executions
    ADD COLUMN prompt_version_id UUID;

ALTER TABLE ai_executions
    ADD COLUMN prompt_source VARCHAR(20);

ALTER TABLE ai_executions
    ADD CONSTRAINT fk_ai_executions_prompt_version
        FOREIGN KEY (prompt_version_id)
        REFERENCES ai_prompt_templates (id)
        ON DELETE RESTRICT;

ALTER TABLE ai_executions
    ADD CONSTRAINT ck_ai_executions_prompt_source
        CHECK (prompt_source IS NULL OR prompt_source IN ('DB_VERSION', 'CODE_FALLBACK'));

ALTER TABLE ai_executions
    ADD CONSTRAINT ck_ai_executions_prompt_ref
        CHECK (
            (prompt_source = 'DB_VERSION' AND prompt_version_id IS NOT NULL)
            OR
            (prompt_source = 'CODE_FALLBACK' AND prompt_version_id IS NULL)
            OR
            (prompt_source IS NULL AND prompt_version_id IS NULL)
        );

CREATE INDEX idx_ai_executions_prompt_version
    ON ai_executions (prompt_version_id);
