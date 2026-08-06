ALTER TABLE audit_logs ADD COLUMN metadata JSONB;
ALTER TABLE audit_logs ADD COLUMN request_id VARCHAR(100);

ALTER TABLE audit_logs ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE audit_logs ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX idx_audit_logs_actor_id ON audit_logs (actor_id);
CREATE INDEX idx_audit_logs_target ON audit_logs (target_resource, target_id);
CREATE INDEX idx_audit_logs_request_id ON audit_logs (request_id);
CREATE INDEX idx_audit_logs_action_created_at ON audit_logs (action, created_at DESC);
