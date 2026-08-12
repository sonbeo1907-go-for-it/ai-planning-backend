package com.codegym.aiplanning.entity.roadmap;

import com.codegym.aiplanning.common.entity.BaseEntity;
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
@Table(name = "roadmap_versions")
public class RoadmapVersion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "roadmap_id", nullable = false)
    private Roadmap roadmap;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RoadmapVersionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RoadmapVersionOrigin origin;

    @Column(name = "draft_slot_roadmap_id")
    private UUID draftSlotRoadmapId;

    @Column(name = "activated_at")
    private Instant activatedAt;

    protected RoadmapVersion() {}

    public static RoadmapVersion draft(
            Roadmap roadmap, int versionNumber, RoadmapVersionOrigin origin) {
        RoadmapVersion version = new RoadmapVersion();
        version.roadmap = roadmap;
        version.versionNumber = versionNumber;
        version.status = RoadmapVersionStatus.DRAFT;
        version.origin = origin;
        version.draftSlotRoadmapId = roadmap.getId();
        return version;
    }

    public Roadmap getRoadmap() {
        return roadmap;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public RoadmapVersionStatus getStatus() {
        return status;
    }

    public RoadmapVersionOrigin getOrigin() {
        return origin;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public boolean isDraft() {
        return status == RoadmapVersionStatus.DRAFT;
    }

    public void activate(Instant activatedAt) {
        if (status != RoadmapVersionStatus.DRAFT) {
            throw new IllegalStateException("Only a draft Roadmap version can be activated.");
        }
        status = RoadmapVersionStatus.ACTIVE;
        draftSlotRoadmapId = null;
        this.activatedAt = activatedAt;
    }

    public void supersede() {
        if (status != RoadmapVersionStatus.ACTIVE) {
            throw new IllegalStateException("Only an active Roadmap version can be superseded.");
        }
        status = RoadmapVersionStatus.SUPERSEDED;
    }
}
