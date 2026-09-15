CREATE TABLE daily_plan_task_steps (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    daily_plan_item_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    guidance TEXT,
    order_index INTEGER NOT NULL,
    estimated_minutes INTEGER,
    required BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_daily_plan_task_steps_item
        FOREIGN KEY (daily_plan_item_id)
        REFERENCES daily_plan_items (id) ON DELETE CASCADE,
    CONSTRAINT uk_daily_plan_task_steps_item_order
        UNIQUE (daily_plan_item_id, order_index),
    CONSTRAINT ck_daily_plan_task_steps_title
        CHECK (char_length(trim(title)) > 0),
    CONSTRAINT ck_daily_plan_task_steps_order
        CHECK (order_index >= 0),
    CONSTRAINT ck_daily_plan_task_steps_estimate
        CHECK (estimated_minutes IS NULL OR estimated_minutes > 0)
);

CREATE TABLE daily_plan_task_step_states (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    task_step_id UUID NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_daily_plan_task_step_states_step UNIQUE (task_step_id),
    CONSTRAINT fk_daily_plan_task_step_states_step
        FOREIGN KEY (task_step_id)
        REFERENCES daily_plan_task_steps (id) ON DELETE CASCADE,
    CONSTRAINT ck_daily_plan_task_step_states_completion CHECK (
        (completed = TRUE AND completed_at IS NOT NULL)
        OR (completed = FALSE AND completed_at IS NULL)
    )
);
