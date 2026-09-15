package com.codegym.aiplanning.entity.daily;

import com.codegym.aiplanning.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "daily_plan_task_step_states")
public class DailyPlanTaskStepState extends BaseEntity {

    @Column(name = "task_step_id", nullable = false, unique = true)
    private UUID taskStepId;

    @Column(nullable = false)
    private Boolean completed;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected DailyPlanTaskStepState() {}

    public static DailyPlanTaskStepState create(UUID taskStepId) {
        DailyPlanTaskStepState state = new DailyPlanTaskStepState();
        state.taskStepId = taskStepId;
        state.completed = false;
        return state;
    }

    public void setCompleted(boolean completed, Instant changedAt) {
        if (completed) {
            if (!Boolean.TRUE.equals(this.completed)) {
                this.completedAt = changedAt;
            }
        } else {
            this.completedAt = null;
        }
        this.completed = completed;
    }

    public UUID getTaskStepId() {
        return taskStepId;
    }

    public Boolean getCompleted() {
        return completed;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}

