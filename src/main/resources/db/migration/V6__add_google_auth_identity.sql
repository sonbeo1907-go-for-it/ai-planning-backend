ALTER TABLE user_accounts
    ALTER COLUMN password_hash DROP NOT NULL;

CREATE TABLE auth_identities (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    user_id UUID NOT NULL,
    provider VARCHAR(30) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    provider_email VARCHAR(254) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_auth_identities_user
        FOREIGN KEY (user_id) REFERENCES user_accounts (id),
    CONSTRAINT uk_auth_identities_provider_subject
        UNIQUE (provider, provider_subject),
    CONSTRAINT uk_auth_identities_user_provider
        UNIQUE (user_id, provider),
    CONSTRAINT ck_auth_identities_provider
        CHECK (provider IN ('GOOGLE'))
);

CREATE INDEX idx_auth_identities_user
    ON auth_identities (user_id);
