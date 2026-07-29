ALTER TABLE user_accounts
    ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0;

ALTER TABLE user_accounts
    ADD COLUMN login_blocked_until TIMESTAMP WITH TIME ZONE;

ALTER TABLE user_accounts
    ADD CONSTRAINT ck_user_accounts_failed_login_attempts
        CHECK (failed_login_attempts >= 0);
