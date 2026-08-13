-- Add roadmap_id and timestamps to daily_plans
ALTER TABLE daily_plans ADD COLUMN roadmap_id UUID;
ALTER TABLE daily_plans ADD COLUMN started_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE daily_plans ADD COLUMN completed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE daily_plans ADD COLUMN cancelled_at TIMESTAMP WITH TIME ZONE;

-- Add content_hash to daily_plan_versions
ALTER TABLE daily_plan_versions ADD COLUMN content_hash VARCHAR(64);

-- Add roadmap_item_id to daily_plan_items
ALTER TABLE daily_plan_items ADD COLUMN roadmap_item_id UUID;

-- Update progress_entries
ALTER TABLE progress_entries ADD COLUMN actual_result TEXT;
ALTER TABLE progress_entries ADD COLUMN difficulty INTEGER;
ALTER TABLE progress_entries ADD COLUMN understanding_rating INTEGER;
ALTER TABLE progress_entries ADD COLUMN note TEXT;
ALTER TABLE progress_entries ADD COLUMN supersedes_entry_id UUID;

-- Add constraints to progress_entries
ALTER TABLE progress_entries ADD CONSTRAINT fk_pe_supersedes FOREIGN KEY (supersedes_entry_id) REFERENCES progress_entries (id) ON DELETE SET NULL;
ALTER TABLE progress_entries ADD CONSTRAINT ck_pe_difficulty CHECK (difficulty BETWEEN 1 AND 5);
ALTER TABLE progress_entries ADD CONSTRAINT ck_pe_understanding CHECK (understanding_rating BETWEEN 1 AND 5);
