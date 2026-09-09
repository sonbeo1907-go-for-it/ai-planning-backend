package com.codegym.aiplanning.controller.roadmap.dto;

public record RoadmapItemProgressResponse(
        int completionPercentage,
        int completedUnitsCount,
        int totalUnitsCount,
        boolean isCompleted) {

    public static RoadmapItemProgressResponse empty() {
        return new RoadmapItemProgressResponse(0, 0, 0, false);
    }
}
