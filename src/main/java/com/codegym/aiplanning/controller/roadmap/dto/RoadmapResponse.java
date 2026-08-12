package com.codegym.aiplanning.controller.roadmap.dto;

import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RoadmapResponse(
        UUID id,
        long version,
        String title,
        String description,
        RoadmapStatus status,
        UUID activeVersionId,
        List<RoadmapVersionResponse> versions,
        Instant createdAt,
        Instant updatedAt) {

    public static RoadmapResponse from(
            Roadmap roadmap, List<RoadmapVersionResponse> versions) {
        return new RoadmapResponse(
                roadmap.getId(),
                roadmap.getVersion(),
                roadmap.getTitle(),
                roadmap.getDescription(),
                roadmap.getStatus(),
                roadmap.getActiveVersionId(),
                versions == null ? List.of() : versions,
                roadmap.getCreatedAt(),
                roadmap.getUpdatedAt());
    }

    @Override
    public String toString() {
        return "RoadmapResponse[id=" + id
                + ", status=" + status
                + ", personalLearningData=<redacted>]";
    }
}
