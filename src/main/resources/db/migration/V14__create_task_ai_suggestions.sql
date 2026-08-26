CREATE TABLE daily_plan_item_ai_suggestions (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    daily_plan_item_id UUID NOT NULL UNIQUE,
    short_description TEXT NOT NULL,
    generation_request_key VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_dpi_ai_suggestions_item
        FOREIGN KEY (daily_plan_item_id) REFERENCES daily_plan_items (id) ON DELETE CASCADE
);

CREATE TABLE daily_plan_item_ai_suggestion_steps (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    suggestion_id UUID NOT NULL,
    order_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_dpi_ai_steps_suggestion
        FOREIGN KEY (suggestion_id) REFERENCES daily_plan_item_ai_suggestions (id) ON DELETE CASCADE,
    CONSTRAINT uk_dpi_ai_steps_suggestion_order UNIQUE (suggestion_id, order_index),
    CONSTRAINT ck_dpi_ai_steps_order CHECK (order_index >= 0)
);

CREATE TABLE daily_plan_item_ai_suggestion_references (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    suggestion_id UUID NOT NULL,
    reference_type VARCHAR(20) NOT NULL,
    title VARCHAR(255) NOT NULL,
    url VARCHAR(2048),
    document_id UUID,
    verified BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_dpi_ai_refs_suggestion
        FOREIGN KEY (suggestion_id) REFERENCES daily_plan_item_ai_suggestions (id) ON DELETE CASCADE,
    CONSTRAINT ck_dpi_ai_refs_type CHECK (reference_type IN ('DOCUMENT', 'LINK')),
    CONSTRAINT ck_dpi_ai_refs_shape CHECK (
        (reference_type = 'DOCUMENT' AND document_id IS NOT NULL AND url IS NULL)
        OR (reference_type = 'LINK' AND url IS NOT NULL AND document_id IS NULL)
    ),
    CONSTRAINT ck_dpi_ai_refs_verified CHECK (
        (reference_type = 'DOCUMENT' AND verified)
        OR (reference_type = 'LINK' AND NOT verified)
    )
);

CREATE INDEX idx_dpi_ai_steps_suggestion
    ON daily_plan_item_ai_suggestion_steps (suggestion_id, order_index);

CREATE INDEX idx_dpi_ai_refs_suggestion
    ON daily_plan_item_ai_suggestion_references (suggestion_id);
