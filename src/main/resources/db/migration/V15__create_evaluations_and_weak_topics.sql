CREATE TABLE weak_topics (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    user_id UUID NOT NULL,
    roadmap_id UUID NOT NULL,
    roadmap_item_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'UNRESOLVED',
    trigger_source VARCHAR(30) NOT NULL,
    last_quiz_score NUMERIC(5, 2),
    last_understanding_rating INTEGER,
    unresolved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    mastered_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_weak_topics_user FOREIGN KEY (user_id)
        REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT fk_weak_topics_roadmap FOREIGN KEY (roadmap_id)
        REFERENCES roadmaps (id) ON DELETE CASCADE,
    CONSTRAINT fk_weak_topics_roadmap_item FOREIGN KEY (roadmap_item_id)
        REFERENCES roadmap_items (id) ON DELETE CASCADE,
    CONSTRAINT uk_weak_topics_user_item UNIQUE (user_id, roadmap_item_id),
    CONSTRAINT ck_weak_topics_status CHECK (status IN ('UNRESOLVED', 'IN_REVIEW', 'MASTERED')),
    CONSTRAINT ck_weak_topics_trigger CHECK (trigger_source IN ('QUIZ_FAILED', 'LOW_RATING', 'BOTH'))
);

CREATE INDEX idx_weak_topics_user_status
    ON weak_topics (user_id, roadmap_id, status);

CREATE TABLE daily_evaluations (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    user_id UUID NOT NULL,
    daily_plan_id UUID NOT NULL,
    evaluation_date DATE NOT NULL,
    quiz_score NUMERIC(5, 2),
    quiz_passed BOOLEAN,
    overall_rating INTEGER,
    feedback_note TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_evaluations_user FOREIGN KEY (user_id)
        REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT fk_evaluations_plan FOREIGN KEY (daily_plan_id)
        REFERENCES daily_plans (id) ON DELETE CASCADE,
    CONSTRAINT uk_evaluations_plan UNIQUE (daily_plan_id),
    CONSTRAINT ck_evaluations_rating CHECK (
        overall_rating IS NULL OR (overall_rating >= 1 AND overall_rating <= 5)
    )
);

CREATE INDEX idx_daily_evaluations_user_date
    ON daily_evaluations (user_id, evaluation_date);

CREATE TABLE quizzes (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    user_id UUID NOT NULL,
    daily_plan_id UUID,
    roadmap_id UUID NOT NULL,
    quiz_type VARCHAR(30) NOT NULL DEFAULT 'DAILY_MICRO_QUIZ',
    target_weak_topic_id UUID,
    status VARCHAR(30) NOT NULL DEFAULT 'GENERATED',
    score NUMERIC(5, 2),
    passed BOOLEAN,
    submitted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_quizzes_user FOREIGN KEY (user_id)
        REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT fk_quizzes_plan FOREIGN KEY (daily_plan_id)
        REFERENCES daily_plans (id) ON DELETE SET NULL,
    CONSTRAINT fk_quizzes_roadmap FOREIGN KEY (roadmap_id)
        REFERENCES roadmaps (id) ON DELETE CASCADE,
    CONSTRAINT fk_quizzes_weak_topic FOREIGN KEY (target_weak_topic_id)
        REFERENCES weak_topics (id) ON DELETE SET NULL,
    CONSTRAINT ck_quizzes_type CHECK (quiz_type IN ('DAILY_MICRO_QUIZ', 'MASTERY_CHECK')),
    CONSTRAINT ck_quizzes_status CHECK (status IN ('GENERATED', 'SUBMITTED'))
);

CREATE INDEX idx_quizzes_user_plan
    ON quizzes (user_id, daily_plan_id);

CREATE TABLE quiz_questions (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    quiz_id UUID NOT NULL,
    roadmap_item_id UUID,
    question_text TEXT NOT NULL,
    options_json TEXT NOT NULL,
    correct_option VARCHAR(10) NOT NULL,
    explanation TEXT NOT NULL,
    user_answer VARCHAR(10),
    is_correct BOOLEAN,
    order_index INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_quiz_questions_quiz FOREIGN KEY (quiz_id)
        REFERENCES quizzes (id) ON DELETE CASCADE,
    CONSTRAINT fk_quiz_questions_item FOREIGN KEY (roadmap_item_id)
        REFERENCES roadmap_items (id) ON DELETE SET NULL
);

CREATE INDEX idx_quiz_questions_quiz
    ON quiz_questions (quiz_id);
