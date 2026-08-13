package com.codegym.aiplanning.entity.daily;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "daily_plan_versions")
@EntityListeners(AuditingEntityListener.class)
public class DailyPlanVersion {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "daily_plan_id", nullable = false)
    private UUID dailyPlanId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DailyPlanVersionOrigin origin;

    @Column(name = "available_minutes", nullable = false)
    private Integer availableMinutes;

    @Column(name = "total_planned_minutes", nullable = false)
    private Integer totalPlannedMinutes;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

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
        version.origin = origin != null ? origin : DailyPlanVersionOrigin.MANUAL;
        version.availableMinutes = availableMinutes != null ? availableMinutes : 60;
        version.totalPlannedMinutes = totalPlannedMinutes != null ? totalPlannedMinutes : 0;
        return version;
    }

    public void updateTotalPlannedMinutes(Integer minutes) {
        this.totalPlannedMinutes = minutes != null ? minutes : 0;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDailyPlanId() {
        return dailyPlanId;
    }

    public Integer getVersionNumber() {
        return versionNumber;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getContentHash() {
        return contentHash;
    }
}
