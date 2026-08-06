CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    used_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE
);

CREATE INDEX idx_pwd_reset_user_id ON password_reset_tokens(user_id);
CREATE INDEX idx_pwd_reset_expires_at ON password_reset_tokens(expires_at);
CREATE INDEX idx_pwd_reset_token_hash ON password_reset_tokens(token_hash);
