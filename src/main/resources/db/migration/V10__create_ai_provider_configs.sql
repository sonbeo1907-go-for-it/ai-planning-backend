CREATE TABLE ai_providers (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    code VARCHAR(50) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    base_url VARCHAR(500) NOT NULL,
    protocol VARCHAR(50) NOT NULL,
    credential_strategy VARCHAR(30) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    archived_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_ai_providers_code UNIQUE (code),
    CONSTRAINT ck_ai_providers_code_uppercase CHECK (code = UPPER(code)),
    CONSTRAINT ck_ai_providers_protocol CHECK (protocol IN ('OPENAI_COMPATIBLE')),
    CONSTRAINT ck_ai_providers_credential_strategy CHECK (
        credential_strategy IN ('PRIORITY')
    )
);

CREATE TABLE ai_provider_credentials (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    provider_id UUID NOT NULL,
    label VARCHAR(100) NOT NULL,
    secret_ref VARCHAR(150) NOT NULL,
    priority INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    archived_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_ai_provider_credentials_provider FOREIGN KEY (provider_id)
        REFERENCES ai_providers (id) ON DELETE RESTRICT,
    CONSTRAINT uk_ai_provider_credentials_label UNIQUE (provider_id, label),
    CONSTRAINT ck_ai_provider_credentials_priority CHECK (priority >= 0)
);

CREATE TABLE ai_provider_configs (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    provider_id UUID NOT NULL,
    purpose VARCHAR(50) NOT NULL,
    model VARCHAR(150) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    default_provider BOOLEAN NOT NULL DEFAULT FALSE,
    default_slot_purpose VARCHAR(50),
    timeout_seconds INTEGER NOT NULL,
    max_input_tokens INTEGER NOT NULL,
    max_output_tokens INTEGER NOT NULL,
    temperature DECIMAL(3, 2) NOT NULL,
    archived_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_ai_provider_configs_provider FOREIGN KEY (provider_id)
        REFERENCES ai_providers (id) ON DELETE RESTRICT,
    CONSTRAINT uk_ai_provider_default_per_purpose UNIQUE (default_slot_purpose),
    CONSTRAINT ck_ai_provider_config_purpose CHECK (
        purpose IN (
            'DOCUMENT_EXTRACTION',
            'ROADMAP_GENERATION',
            'DAILY_PLAN_GENERATION',
            'DAILY_PLAN_REVIEW'
        )
    ),
    CONSTRAINT ck_ai_provider_config_timeout CHECK (timeout_seconds BETWEEN 1 AND 120),
    CONSTRAINT ck_ai_provider_config_input_tokens CHECK (max_input_tokens > 0),
    CONSTRAINT ck_ai_provider_config_output_tokens CHECK (max_output_tokens > 0),
    CONSTRAINT ck_ai_provider_config_temperature CHECK (temperature BETWEEN 0 AND 2),
    CONSTRAINT ck_ai_provider_config_default_slot CHECK (
        (
            default_provider = TRUE
            AND enabled = TRUE
            AND archived_at IS NULL
            AND default_slot_purpose = purpose
        )
        OR
        (
            default_provider = FALSE
            AND default_slot_purpose IS NULL
        )
    )
);

CREATE INDEX idx_ai_providers_active
    ON ai_providers (archived_at, enabled, code);

CREATE INDEX idx_ai_provider_credentials_selection
    ON ai_provider_credentials (provider_id, archived_at, enabled, priority);

CREATE INDEX idx_ai_provider_configs_purpose_active
    ON ai_provider_configs (purpose, archived_at, enabled);

CREATE INDEX idx_ai_provider_configs_provider
    ON ai_provider_configs (provider_id, archived_at);
