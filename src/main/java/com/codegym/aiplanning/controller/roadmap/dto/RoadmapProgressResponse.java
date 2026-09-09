package com.codegym.aiplanning.controller.roadmap.dto;

public record RoadmapProgressResponse(
        int completionPercentage,
        int completedItemsCount,
        int totalItemsCount) {

    public static RoadmapProgressResponse empty() {
        return new RoadmapProgressResponse(0, 0, 0);
    }
}
