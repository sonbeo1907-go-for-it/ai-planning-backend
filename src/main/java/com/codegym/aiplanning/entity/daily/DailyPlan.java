package com.codegym.aiplanning.entity.daily;

import com.codegym.aiplanning.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "daily_plans")
public class DailyPlan extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "roadmap_id")
    private UUID roadmapId;

    @Column(name = "plan_date", nullable = false)
    private LocalDate planDate;

    @Column(name = "time_zone_snapshot", nullable = false, length = 50)
    private String timeZoneSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DailyPlanStatus status;

    @Column(name = "active_version_id")
    private UUID activeVersionId;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    protected DailyPlan() {}

    public static DailyPlan create(UUID userId, LocalDate planDate, String timeZoneSnapshot) {
        return create(userId, planDate, timeZoneSnapshot, null);
    }

    public static DailyPlan create(UUID userId, LocalDate planDate, String timeZoneSnapshot, UUID roadmapId) {
        DailyPlan plan = new DailyPlan();
        plan.userId = userId;
        plan.planDate = planDate;
        plan.timeZoneSnapshot = timeZoneSnapshot != null ? timeZoneSnapshot : "UTC";
        plan.status = DailyPlanStatus.DRAFT;
        plan.roadmapId = roadmapId;
        return plan;
    }

    public void updateActiveVersion(UUID versionId) {
        this.activeVersionId = versionId;
    }

    public void activate(UUID versionId) {
        if (this.status != DailyPlanStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT plan can be activated.");
        }
        this.activeVersionId = versionId;
        this.status = DailyPlanStatus.READY;
    }

    public void updateStatus(DailyPlanStatus newStatus) {
        this.status = newStatus;
    }

    public UUID getUserId() {
        return userId;
    }

    public LocalDate getPlanDate() {
        return planDate;
    }

    public String getTimeZoneSnapshot() {
        return timeZoneSnapshot;
    }

    public DailyPlanStatus getStatus() {
        return status;
    }

    public UUID getActiveVersionId() {
        return activeVersionId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public UUID getRoadmapId() {
        return roadmapId;
    }

    public void setRoadmapId(UUID roadmapId) {
        this.roadmapId = roadmapId;
    }
}
