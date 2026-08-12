package com.codegym.aiplanning.controller.roadmap.dto;

import com.codegym.aiplanning.entity.roadmap.RoadmapVersion;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersionOrigin;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RoadmapVersionResponse(
        UUID id,
        long entityVersion,
        int versionNumber,
        RoadmapVersionStatus status,
        RoadmapVersionOrigin origin,
        Instant activatedAt,
        List<RoadmapItemResponse> milestones,
        Instant createdAt,
        Instant updatedAt) {

    public static RoadmapVersionResponse from(
            RoadmapVersion version, List<RoadmapItemResponse> milestones) {
        return new RoadmapVersionResponse(
                version.getId(),
                version.getVersion(),
                version.getVersionNumber(),
                version.getStatus(),
                version.getOrigin(),
                version.getActivatedAt(),
                milestones == null ? List.of() : milestones,
                version.getCreatedAt(),
                version.getUpdatedAt());
    }

    @Override
    public String toString() {
        return "RoadmapVersionResponse[id=" + id
                + ", versionNumber=" + versionNumber
                + ", status=" + status
                + ", personalLearningData=<redacted>]";
    }
}
