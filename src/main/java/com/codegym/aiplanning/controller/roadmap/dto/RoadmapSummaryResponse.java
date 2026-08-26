package com.codegym.aiplanning.controller.roadmap.dto;

import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapStatus;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersion;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RoadmapSummaryResponse(
        UUID id,
        long entityVersion,
        String title,
        String description,
        RoadmapStatus status,
        UUID activeVersionId,
        int versionCount,
        Integer latestVersionNumber,
        Instant createdAt,
        Instant updatedAt) {

    public static RoadmapSummaryResponse from(
            Roadmap roadmap, List<RoadmapVersion> versions) {
        Integer latestVersionNumber = versions.stream()
                .map(RoadmapVersion::getVersionNumber)
                .max(Integer::compareTo)
                .orElse(null);
        return new RoadmapSummaryResponse(
                roadmap.getId(),
                roadmap.getVersion(),
                roadmap.getTitle(),
                roadmap.getDescription(),
                roadmap.getStatus(),
                roadmap.getActiveVersionId(),
                versions.size(),
                latestVersionNumber,
                roadmap.getCreatedAt(),
                roadmap.getUpdatedAt());
    }

    @Override
    public String toString() {
        return "RoadmapSummaryResponse[id=" + id
                + ", status=" + status
                + ", personalLearningData=<redacted>]";
    }
}
