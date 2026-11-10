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

    @Column(name = "source_roadmap_version_id")
    private UUID sourceRoadmapVersionId;

    @Column(name = "activated_at")
    private Instant activatedAt;

    protected RoadmapVersion() {}

    public static RoadmapVersion draft(
            Roadmap roadmap, int versionNumber, RoadmapVersionOrigin origin) {
        return draft(roadmap, versionNumber, origin, null);
    }

    public static RoadmapVersion derivedDraft(
            Roadmap roadmap,
            int versionNumber,
            RoadmapVersionOrigin origin,
            RoadmapVersion sourceVersion) {
        if (sourceVersion == null || sourceVersion.getId() == null) {
            throw new IllegalArgumentException(
                    "A derived Roadmap version requires a persisted source version.");
        }
        return draft(roadmap, versionNumber, origin, sourceVersion.getId());
    }

    private static RoadmapVersion draft(
            Roadmap roadmap,
            int versionNumber,
            RoadmapVersionOrigin origin,
            UUID sourceRoadmapVersionId) {
        RoadmapVersion version = new RoadmapVersion();
        version.roadmap = roadmap;
        version.versionNumber = versionNumber;
        version.status = RoadmapVersionStatus.DRAFT;
        version.origin = origin;
        version.draftSlotRoadmapId = roadmap.getId();
        version.sourceRoadmapVersionId = sourceRoadmapVersionId;
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

    public UUID getSourceRoadmapVersionId() {
        return sourceRoadmapVersionId;
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

    public void supersedeDraft() {
        if (status != RoadmapVersionStatus.DRAFT) {
            throw new IllegalStateException("Only a draft Roadmap version can be superseded as a draft.");
        }
        status = RoadmapVersionStatus.SUPERSEDED;
        draftSlotRoadmapId = null;
    }
}
