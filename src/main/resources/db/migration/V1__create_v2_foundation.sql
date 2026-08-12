CREATE TABLE user_accounts (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100),
    role VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    login_blocked_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_user_accounts_email UNIQUE (email),
    CONSTRAINT ck_user_accounts_email_lowercase CHECK (email = LOWER(email)),
    CONSTRAINT ck_user_accounts_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT ck_user_accounts_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'LOCKED')),
    CONSTRAINT ck_user_accounts_failed_login_attempts CHECK (failed_login_attempts >= 0)
);

CREATE INDEX idx_user_accounts_status ON user_accounts (status);

CREATE TABLE user_profiles (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    user_id UUID NOT NULL,
    display_name VARCHAR(150) NOT NULL,
    time_zone VARCHAR(50) NOT NULL DEFAULT 'UTC',
    locale VARCHAR(35) NOT NULL DEFAULT 'en',
    default_daily_minutes INTEGER NOT NULL DEFAULT 60,
    setup_completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_user_profiles_user UNIQUE (user_id),
    CONSTRAINT fk_user_profiles_user
        FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT ck_user_profiles_default_daily_minutes
        CHECK (default_daily_minutes BETWEEN 1 AND 1440)
);

CREATE TABLE auth_sessions (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    user_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revoke_reason VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_auth_sessions_user
        FOREIGN KEY (user_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_auth_sessions_status CHECK (status IN ('ACTIVE', 'REVOKED'))
);

CREATE INDEX idx_auth_sessions_user_status ON auth_sessions (user_id, status);
CREATE INDEX idx_auth_sessions_expires_at ON auth_sessions (expires_at);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    session_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    replaced_by_token_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_session
        FOREIGN KEY (session_id) REFERENCES auth_sessions (id),
    CONSTRAINT fk_refresh_tokens_replacement
        FOREIGN KEY (replaced_by_token_id) REFERENCES refresh_tokens (id)
);

CREATE INDEX idx_refresh_tokens_session ON refresh_tokens (session_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);

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
    CONSTRAINT ck_auth_identities_provider CHECK (provider IN ('GOOGLE'))
);

CREATE INDEX idx_auth_identities_user ON auth_identities (user_id);

CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    used_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_password_reset_user
        FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE
);

CREATE INDEX idx_pwd_reset_user_id ON password_reset_tokens (user_id);
CREATE INDEX idx_pwd_reset_expires_at ON password_reset_tokens (expires_at);
CREATE INDEX idx_pwd_reset_token_hash ON password_reset_tokens (token_hash);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    actor_id UUID,
    actor_email VARCHAR(254) NOT NULL,
    action VARCHAR(50) NOT NULL,
    target_resource VARCHAR(50) NOT NULL,
    target_id VARCHAR(100),
    details TEXT,
    metadata JSONB,
    request_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_logs_actor_email ON audit_logs (actor_email);
CREATE INDEX idx_audit_logs_actor_id ON audit_logs (actor_id);
CREATE INDEX idx_audit_logs_target ON audit_logs (target_resource, target_id);
CREATE INDEX idx_audit_logs_request_id ON audit_logs (request_id);
CREATE INDEX idx_audit_logs_action_created_at ON audit_logs (action, created_at DESC);
