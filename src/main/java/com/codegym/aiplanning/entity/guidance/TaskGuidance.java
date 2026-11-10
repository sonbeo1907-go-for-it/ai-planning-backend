package com.codegym.aiplanning.entity.guidance;

import com.codegym.aiplanning.common.entity.BaseEntity;
import com.codegym.aiplanning.entity.auth.UserAccount;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "task_guidances")
public class TaskGuidance extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserAccount owner;

    @Column(name = "daily_plan_id", nullable = false)
    private UUID dailyPlanId;

    @Column(name = "daily_plan_version_id", nullable = false)
    private UUID dailyPlanVersionId;

    @Column(name = "daily_plan_item_id", nullable = false, unique = true)
    private UUID dailyPlanItemId;

    protected TaskGuidance() {}

    public static TaskGuidance create(
            UserAccount owner,
            UUID dailyPlanId,
            UUID dailyPlanVersionId,
            UUID dailyPlanItemId) {
        TaskGuidance guidance = new TaskGuidance();
        guidance.owner = Objects.requireNonNull(owner, "owner must not be null");
        guidance.dailyPlanId = Objects.requireNonNull(
                dailyPlanId,
                "dailyPlanId must not be null");
        guidance.dailyPlanVersionId = Objects.requireNonNull(
                dailyPlanVersionId,
                "dailyPlanVersionId must not be null");
        guidance.dailyPlanItemId = Objects.requireNonNull(
                dailyPlanItemId,
                "dailyPlanItemId must not be null");
        return guidance;
    }

    public UserAccount getOwner() {
        return owner;
    }

    public UUID getDailyPlanId() {
        return dailyPlanId;
    }

    public UUID getDailyPlanVersionId() {
        return dailyPlanVersionId;
    }

    public UUID getDailyPlanItemId() {
        return dailyPlanItemId;
    }
}
