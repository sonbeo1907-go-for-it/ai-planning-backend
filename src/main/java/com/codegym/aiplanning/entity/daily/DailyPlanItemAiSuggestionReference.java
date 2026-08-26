package com.codegym.aiplanning.entity.daily;

import com.codegym.aiplanning.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Reference document or link of an AI task suggestion (US-TSK-AI).
 *
 * <p>{@code verified} is {@code true} only for {@link TaskAiReferenceType#DOCUMENT}
 * references that point to one original document supplied in the AI context.
 * External {@code LINK} references are always unverified and receive the
 * "Gợi ý chưa xác minh" badge on the client.
 */
@Entity
@Table(name = "daily_plan_item_ai_suggestion_references")
public class DailyPlanItemAiSuggestionReference extends BaseEntity {

    @Column(name = "suggestion_id", nullable = false)
    private UUID suggestionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 20)
    private TaskAiReferenceType referenceType;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 2048)
    private String url;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(nullable = false)
    private boolean verified;

    protected DailyPlanItemAiSuggestionReference() {}

    public static DailyPlanItemAiSuggestionReference create(
            UUID suggestionId,
            TaskAiReferenceType referenceType,
            String title,
            String url,
            UUID documentId,
            boolean verified) {
        DailyPlanItemAiSuggestionReference reference = new DailyPlanItemAiSuggestionReference();
        reference.suggestionId = suggestionId;
        reference.referenceType = referenceType;
        reference.title = title;
        reference.url = url;
        reference.documentId = documentId;
        reference.verified = verified;
        return reference;
    }

    public UUID getSuggestionId() {
        return suggestionId;
    }

    public TaskAiReferenceType getReferenceType() {
        return referenceType;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public boolean isVerified() {
        return verified;
    }
}
