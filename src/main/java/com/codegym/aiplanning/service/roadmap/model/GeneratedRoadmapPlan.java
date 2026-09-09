package com.codegym.aiplanning.service.roadmap.model;

import java.util.List;

public record GeneratedRoadmapPlan(
        String title,
        String description,
        List<GeneratedMilestone> milestones) {

    public record GeneratedMilestone(
            String title,
            String description,
            int orderIndex,
            List<GeneratedTopic> topics) {}

    public record GeneratedTopic(
            String title,
            String description,
            int orderIndex,
            int estimatedMinutes,
            List<GeneratedLearningUnit> learningUnits) {

        public GeneratedTopic(
                String title,
                String description,
                int orderIndex,
                int estimatedMinutes) {
            this(title, description, orderIndex, estimatedMinutes, List.of());
        }
    }

    public record GeneratedLearningUnit(
            String title,
            String description,
            int orderIndex,
            int estimatedMinutes) {}
}
