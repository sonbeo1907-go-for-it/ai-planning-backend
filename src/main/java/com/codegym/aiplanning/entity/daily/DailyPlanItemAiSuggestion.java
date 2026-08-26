package com.codegym.aiplanning.entity.daily;

import com.codegym.aiplanning.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * One-to-one AI-generated guidance for a DailyPlanItem (US-TSK-AI).
 * Holds the short implementation description; the action checklist and the
 * reference links/documents are stored in the child tables
 * {@code daily_plan_item_ai_suggestion_steps} and
 * {@code daily_plan_item_ai_suggestion_references}.
 */
@Entity
@Table(name = "daily_plan_item_ai_suggestions")
public class DailyPlanItemAiSuggestion extends BaseEntity {

    @Column(name = "daily_plan_item_id", nullable = false, unique = true)
    private UUID dailyPlanItemId;

    @Column(name = "short_description", nullable = false, columnDefinition = "TEXT")
    private String shortDescription;

    @Column(name = "generation_request_key", length = 100)
    private String generationRequestKey;

    protected DailyPlanItemAiSuggestion() {}

    public static DailyPlanItemAiSuggestion create(
            UUID dailyPlanItemId, String shortDescription, String generationRequestKey) {
        DailyPlanItemAiSuggestion suggestion = new DailyPlanItemAiSuggestion();
        suggestion.dailyPlanItemId = dailyPlanItemId;
        suggestion.shortDescription = shortDescription;
        suggestion.generationRequestKey = generationRequestKey;
        return suggestion;
    }

    public void updateContent(String shortDescription, String generationRequestKey) {
        this.shortDescription = shortDescription;
        this.generationRequestKey = generationRequestKey;
    }

    public UUID getDailyPlanItemId() {
        return dailyPlanItemId;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public String getGenerationRequestKey() {
        return generationRequestKey;
    }
}
