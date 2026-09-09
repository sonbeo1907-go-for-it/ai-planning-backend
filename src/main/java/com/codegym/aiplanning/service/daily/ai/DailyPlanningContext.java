package com.codegym.aiplanning.service.daily.ai;

import com.codegym.aiplanning.entity.daily.DailyTaskStatus;
import com.codegym.aiplanning.service.evaluation.WeakTopicContextResolver.WeakTopicPromptContext;
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
        List<LatestTopicOutcome> latestTopicOutcomes,
        List<UnfinishedTask> unfinishedTasks,
        List<WeaknessSignal> weaknessSignals,
        List<WeakTopicPromptContext> unresolvedWeakTopics,
        PreviousPlan previousPlan) {

    public DailyPlanningContext {
        if (recentProgress == null) {
            recentProgress = List.of();
        }
        if (latestTopicOutcomes == null) {
            latestTopicOutcomes = List.of();
        }
        if (unfinishedTasks == null) {
            unfinishedTasks = List.of();
        }
        if (weaknessSignals == null) {
            weaknessSignals = List.of();
        }
        if (unresolvedWeakTopics == null) {
            unresolvedWeakTopics = List.of();
        }
    }

    public DailyPlanningContext(
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
        this(
                dailyPlanId,
                userId,
                targetDate,
                timeZone,
                availableMinutes,
                roadmap,
                recentProgress,
                List.of(),
                unfinishedTasks,
                weaknessSignals,
                List.of(),
                previousPlan);
    }

    public record RoadmapContext(
            UUID roadmapId,
            UUID activeVersionId,
            int versionNumber,
            String title,
            String description,
            List<RoadmapTopic> topics) {}

    public record RoadmapTopic(
            UUID roadmapItemId,
            UUID parentTopicId,
            String parentTopicTitle,
            UUID milestoneId,
            String milestoneTitle,
            String title,
            String description,
            int estimatedMinutes,
            int orderIndex) {

        public RoadmapTopic(
                UUID roadmapItemId,
                UUID milestoneId,
                String milestoneTitle,
                String title,
                String description,
                int estimatedMinutes,
                int orderIndex) {
            this(
                    roadmapItemId,
                    roadmapItemId,
                    title,
                    milestoneId,
                    milestoneTitle,
                    title,
                    description,
                    estimatedMinutes,
                    orderIndex);
        }
    }

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

    /**
     * The most recently recorded outcome for one Roadmap Item within this Roadmap.
     *
     * <p>This application-side snapshot is rebuilt from progress history for every generation. It
     * is not a new persistence model and is not sent to the provider as raw history.
     */
    public record LatestTopicOutcome(
            UUID roadmapItemId,
            DailyTaskStatus status,
            int completionPercentage,
            Integer difficulty,
            Integer understandingRating,
            Instant recordedAt,
            LocalDate planDate) {}

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
