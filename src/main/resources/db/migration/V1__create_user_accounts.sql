CREATE TABLE user_accounts (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    role VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_user_accounts_username UNIQUE (username),
    CONSTRAINT ck_user_accounts_role
        CHECK (role IN ('STUDENT', 'INSTRUCTOR', 'ADMIN')),
    CONSTRAINT ck_user_accounts_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'LOCKED'))
);

CREATE INDEX idx_user_accounts_status ON user_accounts (status);
