package com.codegym.aiplanning.entity.roadmap;

import com.codegym.aiplanning.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "study_unit_progress_snapshots")
public class StudyUnitProgressSnapshot extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "roadmap_study_unit_id", nullable = false)
    private UUID roadmapStudyUnitId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProgressSnapshotStatus status;

    @Column(name = "last_progress_entry_id")
    private UUID lastProgressEntryId;

    protected StudyUnitProgressSnapshot() {}

    public static StudyUnitProgressSnapshot create(
            UUID userId,
            UUID roadmapStudyUnitId,
            ProgressSnapshotStatus status,
            UUID lastProgressEntryId) {
        StudyUnitProgressSnapshot snapshot = new StudyUnitProgressSnapshot();
        snapshot.userId = userId;
        snapshot.roadmapStudyUnitId = roadmapStudyUnitId;
        snapshot.status = status != null ? status : ProgressSnapshotStatus.NOT_STARTED;
        snapshot.lastProgressEntryId = lastProgressEntryId;
        return snapshot;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getRoadmapStudyUnitId() {
        return roadmapStudyUnitId;
    }

    public ProgressSnapshotStatus getStatus() {
        return status;
    }

    public UUID getLastProgressEntryId() {
        return lastProgressEntryId;
    }

    public void updateStatus(ProgressSnapshotStatus newStatus, UUID progressEntryId) {
        this.status = newStatus != null ? newStatus : ProgressSnapshotStatus.NOT_STARTED;
        this.lastProgressEntryId = progressEntryId;
    }
}
