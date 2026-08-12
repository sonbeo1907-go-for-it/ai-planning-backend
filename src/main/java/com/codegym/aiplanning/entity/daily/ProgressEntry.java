package com.codegym.aiplanning.entity.daily;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "progress_entries")
@EntityListeners(AuditingEntityListener.class)
public class ProgressEntry {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "daily_plan_item_id", nullable = false)
    private UUID dailyPlanItemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DailyTaskStatus status;

    @Column(name = "actual_minutes", nullable = false)
    private Integer actualMinutes;

    @Column(name = "completion_percentage", nullable = false)
    private Integer completionPercentage;

    @CreatedDate
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected ProgressEntry() {}

    public static ProgressEntry create(
            UUID userId,
            UUID dailyPlanItemId,
            DailyTaskStatus status,
            Integer actualMinutes,
            Integer completionPercentage) {
        ProgressEntry entry = new ProgressEntry();
        entry.userId = userId;
        entry.dailyPlanItemId = dailyPlanItemId;
        entry.status = status;
        entry.actualMinutes = actualMinutes != null ? actualMinutes : 0;
        entry.completionPercentage = completionPercentage != null ? completionPercentage : (status == DailyTaskStatus.COMPLETED ? 100 : 0);
        return entry;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getDailyPlanItemId() {
        return dailyPlanItemId;
    }

    public DailyTaskStatus getStatus() {
        return status;
    }

    public Integer getActualMinutes() {
        return actualMinutes;
    }

    public Integer getCompletionPercentage() {
        return completionPercentage;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
