ALTER TABLE roadmap_versions
    ADD CONSTRAINT uk_roadmap_versions_id_roadmap UNIQUE (id, roadmap_id);

ALTER TABLE roadmap_items
    ADD CONSTRAINT uk_roadmap_items_id_version UNIQUE (id, roadmap_version_id);

ALTER TABLE daily_plan_versions
    ADD CONSTRAINT uk_daily_plan_versions_id_plan UNIQUE (id, daily_plan_id);

CREATE TABLE weak_topics (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    user_id UUID NOT NULL,
    roadmap_id UUID NOT NULL,
    roadmap_version_id UUID NOT NULL,
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
        REFERENCES user_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_weak_topics_version_roadmap
        FOREIGN KEY (roadmap_version_id, roadmap_id)
        REFERENCES roadmap_versions (id, roadmap_id) ON DELETE RESTRICT,
    CONSTRAINT fk_weak_topics_item_version
        FOREIGN KEY (roadmap_item_id, roadmap_version_id)
        REFERENCES roadmap_items (id, roadmap_version_id) ON DELETE RESTRICT,
    CONSTRAINT uk_weak_topics_user_item UNIQUE (user_id, roadmap_item_id),
    CONSTRAINT ck_weak_topics_status CHECK (status IN ('UNRESOLVED', 'IN_REVIEW', 'MASTERED')),
    CONSTRAINT ck_weak_topics_trigger CHECK (trigger_source IN ('QUIZ_FAILED', 'LOW_RATING', 'BOTH')),
    CONSTRAINT ck_weak_topics_score CHECK (last_quiz_score IS NULL OR last_quiz_score BETWEEN 0 AND 100),
    CONSTRAINT ck_weak_topics_rating CHECK (
        last_understanding_rating IS NULL OR last_understanding_rating BETWEEN 1 AND 5
    )
);

CREATE INDEX idx_weak_topics_user_version_status
    ON weak_topics (user_id, roadmap_version_id, status);

CREATE TABLE daily_evaluations (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    user_id UUID NOT NULL,
    daily_plan_id UUID NOT NULL,
    daily_plan_version_id UUID NOT NULL,
    evaluation_date DATE NOT NULL,
    quiz_score NUMERIC(5, 2),
    quiz_passed BOOLEAN,
    overall_rating INTEGER,
    feedback_note TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_evaluations_user FOREIGN KEY (user_id)
        REFERENCES user_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_evaluations_plan FOREIGN KEY (daily_plan_id)
        REFERENCES daily_plans (id) ON DELETE RESTRICT,
    CONSTRAINT fk_evaluations_version_plan
        FOREIGN KEY (daily_plan_version_id, daily_plan_id)
        REFERENCES daily_plan_versions (id, daily_plan_id) ON DELETE RESTRICT,
    CONSTRAINT uk_evaluations_version UNIQUE (daily_plan_version_id),
    CONSTRAINT ck_evaluations_rating CHECK (
        overall_rating IS NULL OR overall_rating BETWEEN 1 AND 5
    ),
    CONSTRAINT ck_evaluations_score CHECK (
        quiz_score IS NULL OR quiz_score BETWEEN 0 AND 100
    )
);

CREATE INDEX idx_daily_evaluations_user_date
    ON daily_evaluations (user_id, evaluation_date);

CREATE TABLE quizzes (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    user_id UUID NOT NULL,
    daily_plan_id UUID,
    daily_plan_version_id UUID,
    roadmap_id UUID NOT NULL,
    roadmap_version_id UUID NOT NULL,
    quiz_type VARCHAR(30) NOT NULL DEFAULT 'DAILY_MICRO_QUIZ',
    target_weak_topic_id UUID,
    status VARCHAR(30) NOT NULL DEFAULT 'GENERATED',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_quizzes_user FOREIGN KEY (user_id)
        REFERENCES user_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_quizzes_version_plan
        FOREIGN KEY (daily_plan_version_id, daily_plan_id)
        REFERENCES daily_plan_versions (id, daily_plan_id) ON DELETE RESTRICT,
    CONSTRAINT fk_quizzes_version_roadmap
        FOREIGN KEY (roadmap_version_id, roadmap_id)
        REFERENCES roadmap_versions (id, roadmap_id) ON DELETE RESTRICT,
    CONSTRAINT fk_quizzes_weak_topic FOREIGN KEY (target_weak_topic_id)
        REFERENCES weak_topics (id) ON DELETE RESTRICT,
    CONSTRAINT ck_quizzes_type CHECK (quiz_type IN ('DAILY_MICRO_QUIZ', 'MASTERY_CHECK')),
    CONSTRAINT ck_quizzes_status CHECK (status IN ('GENERATED', 'SUBMITTED')),
    CONSTRAINT ck_quizzes_source CHECK (
        (quiz_type = 'DAILY_MICRO_QUIZ'
            AND daily_plan_id IS NOT NULL
            AND daily_plan_version_id IS NOT NULL
            AND target_weak_topic_id IS NULL)
        OR
        (quiz_type = 'MASTERY_CHECK'
            AND daily_plan_id IS NULL
            AND daily_plan_version_id IS NULL
            AND target_weak_topic_id IS NOT NULL)
    )
);

CREATE INDEX idx_quizzes_user_plan_version
    ON quizzes (user_id, daily_plan_version_id, created_at DESC);

CREATE TABLE quiz_questions (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    quiz_id UUID NOT NULL,
    roadmap_item_id UUID NOT NULL,
    question_text TEXT NOT NULL,
    options_json TEXT NOT NULL,
    correct_option VARCHAR(10) NOT NULL,
    explanation TEXT NOT NULL,
    order_index INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_quiz_questions_quiz FOREIGN KEY (quiz_id)
        REFERENCES quizzes (id) ON DELETE CASCADE,
    CONSTRAINT fk_quiz_questions_item FOREIGN KEY (roadmap_item_id)
        REFERENCES roadmap_items (id) ON DELETE RESTRICT,
    CONSTRAINT uk_quiz_questions_order UNIQUE (quiz_id, order_index),
    CONSTRAINT ck_quiz_questions_option CHECK (correct_option IN ('A', 'B', 'C', 'D'))
);

CREATE INDEX idx_quiz_questions_quiz ON quiz_questions (quiz_id);

CREATE TABLE quiz_attempts (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    quiz_id UUID NOT NULL,
    user_id UUID NOT NULL,
    attempt_number INTEGER NOT NULL,
    score NUMERIC(5, 2) NOT NULL,
    passed BOOLEAN NOT NULL,
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_quiz_attempts_quiz FOREIGN KEY (quiz_id)
        REFERENCES quizzes (id) ON DELETE RESTRICT,
    CONSTRAINT fk_quiz_attempts_user FOREIGN KEY (user_id)
        REFERENCES user_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT uk_quiz_attempt_number UNIQUE (quiz_id, user_id, attempt_number),
    CONSTRAINT ck_quiz_attempt_score CHECK (score BETWEEN 0 AND 100),
    CONSTRAINT ck_quiz_attempt_number CHECK (attempt_number > 0)
);

CREATE TABLE quiz_attempt_answers (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    quiz_attempt_id UUID NOT NULL,
    quiz_question_id UUID NOT NULL,
    selected_option VARCHAR(10) NOT NULL,
    correct BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_attempt_answers_attempt FOREIGN KEY (quiz_attempt_id)
        REFERENCES quiz_attempts (id) ON DELETE CASCADE,
    CONSTRAINT fk_attempt_answers_question FOREIGN KEY (quiz_question_id)
        REFERENCES quiz_questions (id) ON DELETE RESTRICT,
    CONSTRAINT uk_attempt_answer_question UNIQUE (quiz_attempt_id, quiz_question_id),
    CONSTRAINT ck_attempt_answer_option CHECK (selected_option IN ('A', 'B', 'C', 'D'))
);

ALTER TABLE ai_provider_configs DROP CONSTRAINT ck_ai_provider_config_purpose;
ALTER TABLE ai_provider_configs ADD CONSTRAINT ck_ai_provider_config_purpose CHECK (
    purpose IN (
        'DOCUMENT_EXTRACTION', 'ROADMAP_GENERATION', 'DAILY_PLAN_GENERATION',
        'DAILY_PLAN_REVIEW', 'QUIZ_GENERATION'
    )
);

ALTER TABLE ai_executions DROP CONSTRAINT ck_ai_execution_purpose;
ALTER TABLE ai_executions ADD CONSTRAINT ck_ai_execution_purpose CHECK (
    purpose IN (
        'DOCUMENT_EXTRACTION', 'ROADMAP_GENERATION', 'DAILY_PLAN_GENERATION',
        'DAILY_PLAN_REVIEW', 'QUIZ_GENERATION'
    )
);

ALTER TABLE ai_executions DROP CONSTRAINT ck_ai_execution_target_result_match;
ALTER TABLE ai_executions DROP CONSTRAINT ck_ai_execution_target_type;
ALTER TABLE ai_executions ADD CONSTRAINT ck_ai_execution_target_type CHECK (
    target_type IN ('ROADMAP', 'DAILY_PLAN', 'DAILY_PLAN_VERSION', 'WEAK_TOPIC')
);

ALTER TABLE ai_executions DROP CONSTRAINT ck_ai_execution_result_type;
ALTER TABLE ai_executions ADD CONSTRAINT ck_ai_execution_result_type CHECK (
    result_type IS NULL OR result_type IN ('ROADMAP_VERSION', 'DAILY_PLAN_VERSION', 'QUIZ')
);

ALTER TABLE ai_executions ADD CONSTRAINT ck_ai_execution_target_result_match CHECK (
    result_type IS NULL
    OR (target_type = 'ROADMAP' AND result_type = 'ROADMAP_VERSION')
    OR (target_type = 'DAILY_PLAN' AND result_type = 'DAILY_PLAN_VERSION')
    OR (target_type IN ('DAILY_PLAN_VERSION', 'WEAK_TOPIC') AND result_type = 'QUIZ')
);
