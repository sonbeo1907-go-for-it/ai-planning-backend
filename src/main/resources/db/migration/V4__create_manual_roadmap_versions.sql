ALTER TABLE roadmaps ADD COLUMN title VARCHAR(200);
ALTER TABLE roadmaps ADD COLUMN description TEXT;
ALTER TABLE roadmaps ADD COLUMN active_version_id UUID;

CREATE TABLE roadmap_versions (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    roadmap_id UUID NOT NULL,
    version_number INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    origin VARCHAR(30) NOT NULL,
    draft_slot_roadmap_id UUID,
    activated_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_roadmap_versions_roadmap
        FOREIGN KEY (roadmap_id) REFERENCES roadmaps (id) ON DELETE CASCADE,
    CONSTRAINT uk_roadmap_versions_number UNIQUE (roadmap_id, version_number),
    CONSTRAINT uk_roadmap_versions_draft_slot UNIQUE (draft_slot_roadmap_id),
    CONSTRAINT ck_roadmap_versions_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'SUPERSEDED')),
    CONSTRAINT ck_roadmap_versions_origin
        CHECK (origin IN ('MANUAL', 'USER_EDITED')),
    CONSTRAINT ck_roadmap_versions_draft_slot CHECK (
        (status = 'DRAFT' AND draft_slot_roadmap_id = roadmap_id AND activated_at IS NULL)
        OR
        (status <> 'DRAFT' AND draft_slot_roadmap_id IS NULL)
    )
);

CREATE INDEX idx_roadmap_versions_roadmap_status
    ON roadmap_versions (roadmap_id, status, version_number DESC);

ALTER TABLE roadmaps ADD CONSTRAINT fk_roadmaps_active_version
    FOREIGN KEY (active_version_id) REFERENCES roadmap_versions (id);

CREATE TABLE roadmap_items (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    roadmap_version_id UUID NOT NULL,
    parent_item_id UUID,
    item_type VARCHAR(30) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    order_index INTEGER NOT NULL,
    estimated_minutes INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_roadmap_items_version
        FOREIGN KEY (roadmap_version_id) REFERENCES roadmap_versions (id) ON DELETE CASCADE,
    CONSTRAINT fk_roadmap_items_parent
        FOREIGN KEY (parent_item_id) REFERENCES roadmap_items (id) ON DELETE CASCADE,
    CONSTRAINT ck_roadmap_items_type CHECK (item_type IN ('MILESTONE', 'TOPIC')),
    CONSTRAINT ck_roadmap_items_order CHECK (order_index >= 0),
    CONSTRAINT ck_roadmap_items_shape CHECK (
        (item_type = 'MILESTONE' AND parent_item_id IS NULL AND estimated_minutes IS NULL)
        OR
        (item_type = 'TOPIC' AND parent_item_id IS NOT NULL AND estimated_minutes > 0)
    )
);

CREATE INDEX idx_roadmap_items_version_parent_order
    ON roadmap_items (roadmap_version_id, parent_item_id, order_index);
