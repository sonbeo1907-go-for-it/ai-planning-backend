ALTER TABLE materials ADD COLUMN archived_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_materials_owner_archived
    ON materials(user_id, archived_at);
