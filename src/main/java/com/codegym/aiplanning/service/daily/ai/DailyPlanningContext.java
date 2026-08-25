package com.codegym.aiplanning.service.daily.ai;

import com.codegym.aiplanning.entity.daily.DailyTaskStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DailyPlanningContext(
        UUID dailyPlanId,
        UUID userId,
        LocalDate targetDate,
        String timeZone,
        int availableMinutes,
        RoadmapContext roadmap,
        List<ProgressSignal> recentProgress,
        List<UnfinishedTask> unfinishedTasks,
        List<WeaknessSignal> weaknessSignals,
        PreviousPlan previousPlan) {

    public record RoadmapContext(
            UUID roadmapId,
            UUID activeVersionId,
            int versionNumber,
            String title,
            String description,
            List<RoadmapTopic> topics) {}

    public record RoadmapTopic(
            UUID roadmapItemId,
            UUID milestoneId,
            String milestoneTitle,
            String title,
            String description,
            int estimatedMinutes,
            int orderIndex) {}

    public record ProgressSignal(
            UUID progressEntryId,
            UUID dailyPlanItemId,
            UUID roadmapItemId,
            String title,
            DailyTaskStatus status,
            int plannedMinutes,
            int actualMinutes,
            int completionPercentage,
            Integer difficulty,
            Integer understandingRating,
            String actualResult,
            Instant recordedAt) {}

    public record UnfinishedTask(
            UUID dailyPlanItemId,
            UUID roadmapItemId,
            String title,
            String description,
            DailyTaskStatus status,
            int plannedMinutes) {}

    public record WeaknessSignal(
            UUID roadmapItemId,
            String title,
            Integer difficulty,
            Integer understandingRating,
            DailyTaskStatus status,
            String reason) {}

    public record PreviousPlan(
            UUID dailyPlanId,
            LocalDate planDate,
            UUID versionId,
            List<UnfinishedTask> unfinishedTasks) {}
}
