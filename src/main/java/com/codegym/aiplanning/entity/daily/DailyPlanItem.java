package com.codegym.aiplanning.entity.daily;

import com.codegym.aiplanning.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "daily_plan_items")
public class DailyPlanItem extends BaseEntity {

    @Column(name = "daily_plan_version_id", nullable = false)
    private UUID dailyPlanVersionId;

    @Column(name = "roadmap_item_id")
    private UUID roadmapItemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DailyTaskCategory category;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "planned_minutes", nullable = false)
    private Integer plannedMinutes;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DailyTaskStatus status;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected DailyPlanItem() {}

    public static DailyPlanItem create(
            UUID dailyPlanVersionId,
            DailyTaskCategory category,
            String title,
            String description,
            Integer plannedMinutes,
            Integer orderIndex) {
        return create(dailyPlanVersionId, category, title, description, plannedMinutes, orderIndex, null);
    }

    public static DailyPlanItem create(
            UUID dailyPlanVersionId,
            DailyTaskCategory category,
            String title,
            String description,
            Integer plannedMinutes,
            Integer orderIndex,
            UUID roadmapItemId) {
        DailyPlanItem item = new DailyPlanItem();
        item.dailyPlanVersionId = dailyPlanVersionId;
        item.category = category != null ? category : DailyTaskCategory.CUSTOM;
        item.title = title;
        item.description = description;
        item.plannedMinutes = plannedMinutes != null ? plannedMinutes : 30;
        item.orderIndex = orderIndex != null ? orderIndex : 0;
        item.status = DailyTaskStatus.NOT_STARTED;
        item.roadmapItemId = roadmapItemId;
        return item;
    }

    public void updateStatus(DailyTaskStatus newStatus) {
        if (newStatus == DailyTaskStatus.COMPLETED) {
            if (status != DailyTaskStatus.COMPLETED) {
                this.completedAt = Instant.now();
            }
        } else {
            this.completedAt = null;
        }
        this.status = newStatus;
    }

    public UUID getDailyPlanVersionId() {
        return dailyPlanVersionId;
    }

    public DailyTaskCategory getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Integer getPlannedMinutes() {
        return plannedMinutes;
    }

    public Integer getOrderIndex() {
        return orderIndex;
    }

    public DailyTaskStatus getStatus() {
        return status;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public UUID getRoadmapItemId() {
        return roadmapItemId;
    }
}
