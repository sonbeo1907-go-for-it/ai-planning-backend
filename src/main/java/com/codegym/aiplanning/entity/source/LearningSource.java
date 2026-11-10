package com.codegym.aiplanning.entity.source;

import com.codegym.aiplanning.common.entity.BaseEntity;
import com.codegym.aiplanning.entity.auth.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "learning_sources")
public class LearningSource extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserAccount owner;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private LearningSourceType sourceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LearningSourceStatus status;

    @Column(name = "content_text", length = 500)
    private String contentText;

    protected LearningSource() {}

    public static LearningSource goalDraft(UserAccount owner) {
        LearningSource source = new LearningSource();
        source.owner = owner;
        source.sourceType = LearningSourceType.GOAL;
        source.status = LearningSourceStatus.DRAFT;
        return source;
    }

    public UserAccount getOwner() {
        return owner;
    }

    public LearningSourceType getSourceType() {
        return sourceType;
    }

    public LearningSourceStatus getStatus() {
        return status;
    }

    public String getContentText() {
        return contentText;
    }

    public void updateGoal(String goal) {
        contentText = goal;
    }

    public void markReady() {
        if (contentText == null || contentText.isBlank()) {
            throw new IllegalStateException("A goal source requires content before it is ready.");
        }
        status = LearningSourceStatus.READY;
    }
}
