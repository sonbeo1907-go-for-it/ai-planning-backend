CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    actor_id UUID,
    actor_username VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL,
    target_resource VARCHAR(50) NOT NULL,
    target_id VARCHAR(100),
    details TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_audit_logs_actor ON audit_logs (actor_username);
CREATE INDEX idx_audit_logs_action ON audit_logs (action);
CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at);
