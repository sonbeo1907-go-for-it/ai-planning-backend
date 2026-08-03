ALTER TABLE user_accounts
    ADD COLUMN email VARCHAR(254);

UPDATE user_accounts
SET email = LOWER(username) || '@legacy.local';

ALTER TABLE user_accounts
    ALTER COLUMN email SET NOT NULL;

ALTER TABLE user_accounts
    ADD CONSTRAINT uk_user_accounts_email UNIQUE (email);

ALTER TABLE user_accounts
    ADD CONSTRAINT ck_user_accounts_email_lowercase CHECK (email = LOWER(email));
