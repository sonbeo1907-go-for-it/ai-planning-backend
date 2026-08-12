ALTER TABLE user_accounts ADD COLUMN created_by VARCHAR(255);
ALTER TABLE user_accounts ADD COLUMN updated_by VARCHAR(255);

ALTER TABLE user_profiles ADD COLUMN created_by VARCHAR(255);
ALTER TABLE user_profiles ADD COLUMN updated_by VARCHAR(255);

ALTER TABLE auth_sessions ADD COLUMN created_by VARCHAR(255);
ALTER TABLE auth_sessions ADD COLUMN updated_by VARCHAR(255);

ALTER TABLE refresh_tokens ADD COLUMN created_by VARCHAR(255);
ALTER TABLE refresh_tokens ADD COLUMN updated_by VARCHAR(255);

ALTER TABLE auth_identities ADD COLUMN created_by VARCHAR(255);
ALTER TABLE auth_identities ADD COLUMN updated_by VARCHAR(255);

ALTER TABLE audit_logs ADD COLUMN created_by VARCHAR(255);
ALTER TABLE audit_logs ADD COLUMN updated_by VARCHAR(255);

ALTER TABLE materials ADD COLUMN created_by VARCHAR(255);
ALTER TABLE materials ADD COLUMN updated_by VARCHAR(255);
