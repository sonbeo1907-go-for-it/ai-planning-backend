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
    CONSTRAINT ck_auth_sessions_status
        CHECK (status IN ('ACTIVE', 'REVOKED'))
);

CREATE INDEX idx_auth_sessions_user_status
    ON auth_sessions (user_id, status);
CREATE INDEX idx_auth_sessions_expires_at
    ON auth_sessions (expires_at);

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

CREATE INDEX idx_refresh_tokens_session
    ON refresh_tokens (session_id);
CREATE INDEX idx_refresh_tokens_expires_at
    ON refresh_tokens (expires_at);
