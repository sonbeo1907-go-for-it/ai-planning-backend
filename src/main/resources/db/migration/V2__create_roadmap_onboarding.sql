CREATE TABLE learning_sources (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    owner_id UUID NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    content_text VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_learning_sources_owner
        FOREIGN KEY (owner_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT ck_learning_sources_type CHECK (source_type IN ('GOAL')),
    CONSTRAINT ck_learning_sources_status CHECK (status IN ('DRAFT', 'READY')),
    CONSTRAINT ck_learning_sources_ready_content CHECK (
        status <> 'READY' OR (content_text IS NOT NULL AND LENGTH(TRIM(content_text)) > 0)
    )
);

CREATE INDEX idx_learning_sources_owner_status_updated
    ON learning_sources (owner_id, status, updated_at DESC);

CREATE TABLE roadmaps (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    owner_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    proficiency_level VARCHAR(30),
    daily_commitment_minutes INTEGER,
    expected_duration_days INTEGER,
    onboarding_completed_at TIMESTAMP WITH TIME ZONE,
    onboarding_slot_owner_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_roadmaps_owner
        FOREIGN KEY (owner_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT ck_roadmaps_status
        CHECK (status IN ('ONBOARDING', 'DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_roadmaps_proficiency
        CHECK (proficiency_level IS NULL OR proficiency_level IN ('BEGINNER', 'BASIC', 'INTERMEDIATE')),
    CONSTRAINT ck_roadmaps_daily_commitment
        CHECK (daily_commitment_minutes IS NULL OR daily_commitment_minutes IN (30, 60, 120)),
    CONSTRAINT ck_roadmaps_expected_duration
        CHECK (expected_duration_days IS NULL OR expected_duration_days IN (30, 60, 90)),
    CONSTRAINT ck_roadmaps_onboarding_slot CHECK (
        (status = 'ONBOARDING'
            AND onboarding_slot_owner_id = owner_id
            AND onboarding_completed_at IS NULL)
        OR
        (status <> 'ONBOARDING' AND onboarding_slot_owner_id IS NULL)
    ),
    CONSTRAINT uk_roadmaps_owner_onboarding UNIQUE (onboarding_slot_owner_id)
);

CREATE INDEX idx_roadmaps_owner_status_updated
    ON roadmaps (owner_id, status, updated_at DESC);

CREATE TABLE roadmap_sources (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    roadmap_id UUID NOT NULL,
    learning_source_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_roadmap_sources_roadmap
        FOREIGN KEY (roadmap_id) REFERENCES roadmaps (id) ON DELETE CASCADE,
    CONSTRAINT fk_roadmap_sources_learning_source
        FOREIGN KEY (learning_source_id) REFERENCES learning_sources (id),
    CONSTRAINT uk_roadmap_sources_roadmap_source
        UNIQUE (roadmap_id, learning_source_id)
);

CREATE INDEX idx_roadmap_sources_learning_source
    ON roadmap_sources (learning_source_id);
