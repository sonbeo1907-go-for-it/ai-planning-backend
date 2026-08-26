package com.codegym.aiplanning.entity.roadmap;

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
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "roadmaps")
public class Roadmap extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserAccount owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RoadmapStatus status;

    @Column(length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "active_version_id")
    private UUID activeVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "proficiency_level", length = 30)
    private ProficiencyLevel proficiencyLevel;

    @Column(name = "daily_commitment_minutes")
    private Integer dailyCommitmentMinutes;

    @Column(name = "expected_duration_days")
    private Integer expectedDurationDays;

    @Column(name = "onboarding_completed_at")
    private Instant onboardingCompletedAt;

    @Column(name = "onboarding_slot_owner_id")
    private UUID onboardingSlotOwnerId;

    protected Roadmap() {}

    public static Roadmap beginOnboarding(UserAccount owner) {
        Roadmap roadmap = new Roadmap();
        roadmap.owner = owner;
        roadmap.status = RoadmapStatus.ONBOARDING;
        roadmap.onboardingSlotOwnerId = owner.getId();
        return roadmap;
    }

    public static Roadmap manualDraft(
            UserAccount owner, String title, String description) {
        Roadmap roadmap = new Roadmap();
        roadmap.owner = owner;
        roadmap.status = RoadmapStatus.DRAFT;
        roadmap.title = title;
        roadmap.description = description;
        return roadmap;
    }

    public UserAccount getOwner() {
        return owner;
    }

    public RoadmapStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public UUID getActiveVersionId() {
        return activeVersionId;
    }

    public ProficiencyLevel getProficiencyLevel() {
        return proficiencyLevel;
    }

    public Integer getDailyCommitmentMinutes() {
        return dailyCommitmentMinutes;
    }

    public Integer getExpectedDurationDays() {
        return expectedDurationDays;
    }

    public Instant getOnboardingCompletedAt() {
        return onboardingCompletedAt;
    }

    public boolean isOnboardingComplete() {
        return onboardingCompletedAt != null;
    }

    public void updateMetadata(String title, String description) {
        if (status == RoadmapStatus.ONBOARDING || status == RoadmapStatus.ARCHIVED) {
            throw new IllegalStateException(
                    "Roadmap metadata cannot be edited in its current state.");
        }
        this.title = title;
        this.description = description;
    }

    public void updateOnboarding(
            ProficiencyLevel proficiencyLevel,
            Integer dailyCommitmentMinutes,
            Integer expectedDurationDays) {
        if (status != RoadmapStatus.ONBOARDING) {
            throw new IllegalStateException("Completed Roadmap onboarding cannot be edited.");
        }
        if (proficiencyLevel != null) {
            this.proficiencyLevel = proficiencyLevel;
        }
        if (dailyCommitmentMinutes != null) {
            this.dailyCommitmentMinutes = dailyCommitmentMinutes;
        }
        if (expectedDurationDays != null) {
            this.expectedDurationDays = expectedDurationDays;
        }
    }

    public void completeOnboarding(Instant completedAt) {
        if (status == RoadmapStatus.DRAFT && onboardingCompletedAt != null) {
            return;
        }
        if (status != RoadmapStatus.ONBOARDING) {
            throw new IllegalStateException("Roadmap is not in onboarding.");
        }
        status = RoadmapStatus.DRAFT;
        onboardingCompletedAt = completedAt;
        onboardingSlotOwnerId = null;
    }

    public void activateVersion(UUID versionId) {
        if (status == RoadmapStatus.ONBOARDING || status == RoadmapStatus.ARCHIVED) {
            throw new IllegalStateException("Roadmap cannot activate a version in its current state.");
        }
        activeVersionId = versionId;
        status = RoadmapStatus.ACTIVE;
    }
}
