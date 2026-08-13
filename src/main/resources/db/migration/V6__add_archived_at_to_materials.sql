-- US-SRC-01.4: Add archived_at for Soft Delete / Archive Material

ALTER TABLE materials
ADD COLUMN archived_at TIMESTAMP WITH TIME ZONE;

-- Add index to optimize list queries by owner and archived status
CREATE INDEX idx_materials_owner_archived ON materials(user_id, archived_at);
