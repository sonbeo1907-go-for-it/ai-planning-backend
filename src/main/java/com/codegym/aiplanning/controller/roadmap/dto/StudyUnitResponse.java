package com.codegym.aiplanning.controller.roadmap.dto;

import com.codegym.aiplanning.entity.roadmap.ProgressSnapshotStatus;
import com.codegym.aiplanning.entity.roadmap.RoadmapStudyUnit;
import java.util.UUID;

public record StudyUnitResponse(
        UUID id,
        String title,
        int orderIndex,
        ProgressSnapshotStatus status) {

    public static StudyUnitResponse from(
            RoadmapStudyUnit unit, ProgressSnapshotStatus status) {
        return new StudyUnitResponse(
                unit.getId(),
                unit.getTitle(),
                unit.getOrderIndex(),
                status != null ? status : ProgressSnapshotStatus.NOT_STARTED);
    }
}
