package com.codegym.aiplanning.controller.roadmap.dto;

import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RoadmapResponse(
        UUID id,
        long entityVersion,
        String title,
        String description,
        RoadmapStatus status,
        UUID activeVersionId,
        List<RoadmapVersionResponse> versions,
        RoadmapProgressResponse progress,
        Instant createdAt,
        Instant updatedAt) {

    public RoadmapResponse(
            UUID id,
            long entityVersion,
            String title,
            String description,
            RoadmapStatus status,
            UUID activeVersionId,
            List<RoadmapVersionResponse> versions,
            Instant createdAt,
            Instant updatedAt) {
        this(
                id,
                entityVersion,
                title,
                description,
                status,
                activeVersionId,
                versions,
                RoadmapProgressResponse.empty(),
                createdAt,
                updatedAt);
    }

    public static RoadmapResponse from(
            Roadmap roadmap, List<RoadmapVersionResponse> versions) {
        return from(roadmap, versions, RoadmapProgressResponse.empty());
    }

    public static RoadmapResponse from(
            Roadmap roadmap,
            List<RoadmapVersionResponse> versions,
            RoadmapProgressResponse progress) {
        return new RoadmapResponse(
                roadmap.getId(),
                roadmap.getVersion(),
                roadmap.getTitle(),
                roadmap.getDescription(),
                roadmap.getStatus(),
                roadmap.getActiveVersionId(),
                versions == null ? List.of() : versions,
                progress == null ? RoadmapProgressResponse.empty() : progress,
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
