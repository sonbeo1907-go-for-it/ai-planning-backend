ALTER TABLE roadmap_versions DROP CONSTRAINT ck_roadmap_versions_origin;

ALTER TABLE roadmap_versions
    ADD CONSTRAINT ck_roadmap_versions_origin
        CHECK (origin IN ('MANUAL', 'USER_EDITED', 'AI_GENERATED', 'AI_REGENERATED'));

ALTER TABLE roadmap_sources
    ALTER COLUMN learning_source_id DROP NOT NULL;

ALTER TABLE roadmap_sources
    ADD COLUMN material_id UUID;

ALTER TABLE roadmap_sources
    ADD CONSTRAINT fk_roadmap_sources_material
        FOREIGN KEY (material_id) REFERENCES materials (id);

ALTER TABLE roadmap_sources
    ADD CONSTRAINT uk_roadmap_sources_roadmap_material
        UNIQUE (roadmap_id, material_id);

ALTER TABLE roadmap_sources
    ADD CONSTRAINT ck_roadmap_sources_exactly_one_source CHECK (
        (learning_source_id IS NOT NULL AND material_id IS NULL)
        OR (learning_source_id IS NULL AND material_id IS NOT NULL)
    );

CREATE INDEX idx_roadmap_sources_material
    ON roadmap_sources (material_id);
