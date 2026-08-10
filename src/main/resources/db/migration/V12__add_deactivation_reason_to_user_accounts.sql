ALTER TABLE user_accounts
ADD COLUMN deactivation_reason_code VARCHAR(50);

ALTER TABLE user_accounts
ADD COLUMN deactivation_reason_note VARCHAR(500);
