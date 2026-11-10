package com.codegym.aiplanning.entity.daily;

import com.codegym.aiplanning.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "daily_plan_task_steps")
public class DailyPlanTaskStep extends BaseEntity {

    @Column(name = "daily_plan_item_id", nullable = false)
    private UUID dailyPlanItemId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String guidance;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    @Column(nullable = false)
    private Boolean required;

    protected DailyPlanTaskStep() {}

    public static DailyPlanTaskStep create(
            UUID dailyPlanItemId,
            String title,
            String guidance,
            Integer orderIndex,
            Integer estimatedMinutes,
            Boolean required) {
        DailyPlanTaskStep step = new DailyPlanTaskStep();
        step.dailyPlanItemId = dailyPlanItemId;
        step.title = title;
        step.guidance = guidance;
        step.orderIndex = orderIndex;
        step.estimatedMinutes = estimatedMinutes;
        step.required = required != null ? required : true;
        return step;
    }

    public DailyPlanTaskStep copyForItem(UUID targetDailyPlanItemId) {
        return create(
                targetDailyPlanItemId,
                title,
                guidance,
                orderIndex,
                estimatedMinutes,
                required);
    }

    public void updateDraftDetails(
            String title,
            String guidance,
            Integer orderIndex,
            Integer estimatedMinutes,
            Boolean required) {
        this.title = title;
        this.guidance = guidance;
        this.orderIndex = orderIndex;
        this.estimatedMinutes = estimatedMinutes;
        this.required = required;
    }

    public void updateOrderIndex(Integer orderIndex) {
        this.orderIndex = orderIndex;
    }

    public UUID getDailyPlanItemId() {
        return dailyPlanItemId;
    }

    public String getTitle() {
        return title;
    }

    public String getGuidance() {
        return guidance;
    }

    public Integer getOrderIndex() {
        return orderIndex;
    }

    public Integer getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public Boolean getRequired() {
        return required;
    }
}
