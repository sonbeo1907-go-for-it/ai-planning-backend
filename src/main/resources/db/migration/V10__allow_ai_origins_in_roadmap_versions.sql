ALTER TABLE roadmap_versions DROP CONSTRAINT IF EXISTS ck_roadmap_versions_origin;
ALTER TABLE roadmap_versions ADD CONSTRAINT ck_roadmap_versions_origin
    CHECK (origin IN ('MANUAL', 'USER_EDITED', 'AI_GENERATED', 'AI_REGENERATED'));
