CREATE TABLE quiz_review_items (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    quiz_id UUID NOT NULL,
    daily_plan_item_id UUID NOT NULL,
    roadmap_item_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_quiz_review_items_quiz FOREIGN KEY (quiz_id)
        REFERENCES quizzes (id) ON DELETE CASCADE,
    CONSTRAINT fk_quiz_review_items_item FOREIGN KEY (daily_plan_item_id)
        REFERENCES daily_plan_items (id) ON DELETE CASCADE,
    CONSTRAINT fk_quiz_review_items_roadmap_item FOREIGN KEY (roadmap_item_id)
        REFERENCES roadmap_items (id) ON DELETE RESTRICT,
    CONSTRAINT uk_quiz_review_items_quiz_item UNIQUE (quiz_id, daily_plan_item_id)
);

CREATE INDEX idx_quiz_review_items_quiz ON quiz_review_items (quiz_id);
CREATE INDEX idx_quiz_review_items_item ON quiz_review_items (daily_plan_item_id);
