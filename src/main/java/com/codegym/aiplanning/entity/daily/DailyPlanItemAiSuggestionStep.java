package com.codegym.aiplanning.entity.daily;

import com.codegym.aiplanning.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * One ordered action-checklist step of an AI task suggestion (US-TSK-AI).
 */
@Entity
@Table(name = "daily_plan_item_ai_suggestion_steps")
public class DailyPlanItemAiSuggestionStep extends BaseEntity {

    @Column(name = "suggestion_id", nullable = false)
    private UUID suggestionId;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    protected DailyPlanItemAiSuggestionStep() {}

    public static DailyPlanItemAiSuggestionStep create(
            UUID suggestionId, Integer orderIndex, String content) {
        DailyPlanItemAiSuggestionStep step = new DailyPlanItemAiSuggestionStep();
        step.suggestionId = suggestionId;
        step.orderIndex = orderIndex;
        step.content = content;
        return step;
    }

    public UUID getSuggestionId() {
        return suggestionId;
    }

    public Integer getOrderIndex() {
        return orderIndex;
    }

    public String getContent() {
        return content;
    }
}
