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
@Table(name = "daily_plan_versions")
public class DailyPlanVersion extends BaseEntity {

    @Column(name = "daily_plan_id", nullable = false)
    private UUID dailyPlanId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DailyPlanVersionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DailyPlanVersionOrigin origin;

    @Column(name = "available_minutes", nullable = false)
    private Integer availableMinutes;

    @Column(name = "total_planned_minutes", nullable = false)
    private Integer totalPlannedMinutes;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "draft_slot_daily_plan_id")
    private UUID draftSlotDailyPlanId;

    @Column(name = "active_slot_daily_plan_id")
    private UUID activeSlotDailyPlanId;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    protected DailyPlanVersion() {}

    public static DailyPlanVersion create(
            UUID dailyPlanId,
            Integer versionNumber,
            DailyPlanVersionOrigin origin,
            Integer availableMinutes,
            Integer totalPlannedMinutes) {
        DailyPlanVersion version = new DailyPlanVersion();
        version.dailyPlanId = dailyPlanId;
        version.versionNumber = versionNumber != null ? versionNumber : 1;
        version.status = DailyPlanVersionStatus.DRAFT;
        version.origin = origin != null ? origin : DailyPlanVersionOrigin.MANUAL;
        version.availableMinutes = availableMinutes != null ? availableMinutes : 60;
        version.totalPlannedMinutes = totalPlannedMinutes != null ? totalPlannedMinutes : 0;
        version.draftSlotDailyPlanId = dailyPlanId;
        return version;
    }

    public void updateTotalPlannedMinutes(Integer minutes) {
        requireDraft();
        this.totalPlannedMinutes = minutes != null ? minutes : 0;
    }

    public void activate(Instant activatedAt) {
        requireDraft();
        status = DailyPlanVersionStatus.ACTIVE;
        draftSlotDailyPlanId = null;
        activeSlotDailyPlanId = dailyPlanId;
        this.activatedAt = activatedAt;
    }

    public void supersede(Instant supersededAt) {
        if (status != DailyPlanVersionStatus.ACTIVE) {
            throw new IllegalStateException("Only an active Daily Plan version can be superseded.");
        }
        status = DailyPlanVersionStatus.SUPERSEDED;
        activeSlotDailyPlanId = null;
        this.supersededAt = supersededAt;
    }

    public boolean isDraft() {
        return status == DailyPlanVersionStatus.DRAFT;
    }

    private void requireDraft() {
        if (!isDraft()) {
            throw new IllegalStateException("Only a draft Daily Plan version can be modified.");
        }
    }

    public UUID getDailyPlanId() {
        return dailyPlanId;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public DailyPlanVersionStatus getStatus() {
        return status;
    }

    public DailyPlanVersionOrigin getOrigin() {
        return origin;
    }

    public Integer getAvailableMinutes() {
        return availableMinutes;
    }

    public Integer getTotalPlannedMinutes() {
        return totalPlannedMinutes;
    }

    public String getContentHash() {
        return contentHash;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public Instant getSupersededAt() {
        return supersededAt;
    }
}
