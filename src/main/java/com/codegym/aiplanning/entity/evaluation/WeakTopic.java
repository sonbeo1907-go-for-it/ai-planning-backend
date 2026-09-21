package com.codegym.aiplanning.entity.evaluation;

import com.codegym.aiplanning.common.entity.BaseEntity;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapItem;
import com.codegym.aiplanning.entity.roadmap.RoadmapItemType;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Entity
@Table(name = "weak_topics")
public class WeakTopic extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "roadmap_id", nullable = false)
    private Roadmap roadmap;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "roadmap_version_id", nullable = false)
    private RoadmapVersion roadmapVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "roadmap_item_id", nullable = false)
    private RoadmapItem roadmapItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WeakTopicStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_source", nullable = false, length = 30)
    private WeakTopicTrigger triggerSource;

    @Column(name = "last_quiz_score", precision = 5, scale = 2)
    private BigDecimal lastQuizScore;

    @Column(name = "last_mastery_score", precision = 5, scale = 2)
    private BigDecimal lastMasteryScore;

    @Column(name = "last_understanding_rating")
    private Integer lastUnderstandingRating;

    @Column(name = "unresolved_at", nullable = false)
    private Instant unresolvedAt;

    @Column(name = "mastered_at")
    private Instant masteredAt;

    @Column(name = "eligibility_zone", nullable = false, length = 50)
    private String eligibilityZone;

    @Column(name = "eligible_on", nullable = false)
    private LocalDate eligibleOn;

    protected WeakTopic() {}

    public static WeakTopic create(
            UserAccount user,
            Roadmap roadmap,
            RoadmapVersion roadmapVersion,
            RoadmapItem roadmapItem,
            WeakTopicTrigger triggerSource,
            BigDecimal quizScore,
            Integer understandingRating,
            Instant unresolvedAt,
            String eligibilityZone) {
        if (roadmapItem == null
                || roadmapItem.getItemType() != RoadmapItemType.LEARNING_UNIT) {
            throw new IllegalArgumentException(
                    "A Weak Topic signal must target a Learning Unit.");
        }
        if (roadmapVersion == null
                || roadmapItem.getRoadmapVersion() == null
                || !roadmapItem.getRoadmapVersion().getId().equals(roadmapVersion.getId())) {
            throw new IllegalArgumentException(
                    "A Weak Topic signal and its Learning Unit must belong to the same Roadmap version.");
        }
        if (roadmap == null
                || roadmapVersion.getRoadmap() == null
                || !roadmapVersion.getRoadmap().getId().equals(roadmap.getId())) {
            throw new IllegalArgumentException(
                    "A Weak Topic signal must belong to the Learning Unit's Roadmap.");
        }
        WeakTopic weakTopic = new WeakTopic();
        weakTopic.user = user;
        weakTopic.roadmap = roadmap;
        weakTopic.roadmapVersion = roadmapVersion;
        weakTopic.roadmapItem = roadmapItem;
        weakTopic.status = WeakTopicStatus.UNRESOLVED;
        weakTopic.triggerSource = triggerSource;
        weakTopic.lastQuizScore = quizScore;
        weakTopic.lastUnderstandingRating = understandingRating;
        weakTopic.unresolvedAt = unresolvedAt != null ? unresolvedAt : Instant.now();
        weakTopic.setEligibility(weakTopic.unresolvedAt, eligibilityZone);
        return weakTopic;
    }

    public void updateTrigger(
            WeakTopicTrigger triggerSource,
            BigDecimal quizScore,
            Integer understandingRating,
            Instant triggerTime,
            String eligibilityZone) {
        this.triggerSource = triggerSource;
        if (quizScore != null) {
            this.lastQuizScore = quizScore;
        }
        if (understandingRating != null) {
            this.lastUnderstandingRating = understandingRating;
        }
        if (this.status == WeakTopicStatus.MASTERED) {
            this.unresolvedAt = triggerTime != null ? triggerTime : Instant.now();
            setEligibility(this.unresolvedAt, eligibilityZone);
            this.masteredAt = null;
        }
        this.status = WeakTopicStatus.UNRESOLVED;
    }

    public void markMasteryFailed(BigDecimal score) {
        this.status = WeakTopicStatus.UNRESOLVED;
        this.lastMasteryScore = score;
        this.masteredAt = null;
    }

    public boolean isEligibleForMastery(Instant now) {
        return !now.atZone(ZoneId.of(eligibilityZone)).toLocalDate().isBefore(eligibleOn);
    }

    private void setEligibility(Instant detectedAt, String zone) {
        ZoneId zoneId = ZoneId.of(zone);
        this.eligibilityZone = zoneId.getId();
        this.eligibleOn = detectedAt.atZone(zoneId).toLocalDate().plusDays(1);
    }

    public void markInReview() {
        if (this.status == WeakTopicStatus.UNRESOLVED) {
            this.status = WeakTopicStatus.IN_REVIEW;
        }
    }

    public void markMastered(Instant masteredAt, BigDecimal score) {
        this.status = WeakTopicStatus.MASTERED;
        this.masteredAt = masteredAt != null ? masteredAt : Instant.now();
        this.lastMasteryScore = score;
    }

    public UserAccount getUser() {
        return user;
    }

    public Roadmap getRoadmap() {
        return roadmap;
    }

    public RoadmapItem getRoadmapItem() {
        return roadmapItem;
    }

    public RoadmapVersion getRoadmapVersion() {
        return roadmapVersion;
    }

    public WeakTopicStatus getStatus() {
        return status;
    }

    public WeakTopicTrigger getTriggerSource() {
        return triggerSource;
    }

    public BigDecimal getLastQuizScore() {
        return lastQuizScore;
    }

    public BigDecimal getLastMasteryScore() {
        return lastMasteryScore;
    }

    public Integer getLastUnderstandingRating() {
        return lastUnderstandingRating;
    }

    public Instant getUnresolvedAt() {
        return unresolvedAt;
    }

    public Instant getMasteredAt() {
        return masteredAt;
    }

    public LocalDate getEligibleOn() {
        return eligibleOn;
    }

    public String getEligibilityZone() {
        return eligibilityZone;
    }
}
