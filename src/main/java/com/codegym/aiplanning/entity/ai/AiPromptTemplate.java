package com.codegym.aiplanning.entity.ai;

import com.codegym.aiplanning.common.entity.BaseEntity;
import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "ai_prompt_templates")
public class AiPromptTemplate extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AiPurpose purpose;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiPromptStatus status;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "active_slot_purpose", length = 50)
    private String activeSlotPurpose;

    @Column(name = "is_system", nullable = false)
    private boolean isSystem;

    protected AiPromptTemplate() {}

    public static AiPromptTemplate createDraft(AiPurpose purpose, int versionNumber, String content, boolean isSystem) {
        AiPromptTemplate template = new AiPromptTemplate();
        template.purpose = purpose;
        template.versionNumber = versionNumber;
        template.content = content;
        template.status = AiPromptStatus.DRAFT;
        template.isActive = false;
        template.activeSlotPurpose = null;
        template.isSystem = isSystem;
        return template;
    }

    public void updateDraftContent(String newContent) {
        if (this.status != AiPromptStatus.DRAFT) {
            throw new BusinessException(
                    ErrorCode.PROMPT_IMMUTABLE,
                    "Published and archived prompt templates are immutable.");
        }
        this.content = newContent;
    }

    public void publish() {
        if (this.status != AiPromptStatus.DRAFT) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATUS_TRANSITION,
                    "Only DRAFT prompt templates can be published.");
        }
        this.status = AiPromptStatus.PUBLISHED;
        this.isActive = false;
        this.activeSlotPurpose = null;
    }

    public void activate() {
        if (this.status != AiPromptStatus.PUBLISHED) {
            throw new BusinessException(
                    ErrorCode.PROMPT_NOT_PUBLISHED,
                    "Only PUBLISHED prompt templates can be activated.");
        }
        this.isActive = true;
        this.activeSlotPurpose = this.purpose.name();
    }

    public void deactivate() {
        this.isActive = false;
        this.activeSlotPurpose = null;
    }

    public void archive() {
        this.status = AiPromptStatus.ARCHIVED;
        this.isActive = false;
        this.activeSlotPurpose = null;
    }

    public boolean isDraft() {
        return this.status == AiPromptStatus.DRAFT;
    }

    public boolean isPublished() {
        return this.status == AiPromptStatus.PUBLISHED;
    }

    public boolean isArchived() {
        return this.status == AiPromptStatus.ARCHIVED;
    }

    public boolean isActive() {
        return this.isActive;
    }

    public String getActiveSlotPurpose() {
        return activeSlotPurpose;
    }

    public AiPurpose getPurpose() {
        return purpose;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public String getContent() {
        return content;
    }

    public AiPromptStatus getStatus() {
        return status;
    }

    public boolean isSystem() {
        return isSystem;
    }
}
