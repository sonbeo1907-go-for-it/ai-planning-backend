package com.codegym.aiplanning.service.daily.ai;

import com.codegym.aiplanning.entity.daily.DailyTaskStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Minimal, provider-facing view of the Daily Plan context.
 *
 * <p>This record is deliberately separate from {@link DailyPlanningContext}. The database context
 * remains complete for application-side validation, while only bounded and relevant data crosses
 * the AI provider boundary.
 */
public record DailyPlanPromptContext(
        LocalDate targetDate,
        String timeZone,
        int availableMinutes,
        String roadmapTitle,
        List<RelevantTopic> relevantTopics,
        LocalDate previousPlanDate,
        List<UnresolvedTask> unresolvedTasks,
        List<TopicSignal> topicSignals) {

    public record RelevantTopic(
            UUID roadmapItemId,
            String title,
            int estimatedMinutes,
            TopicPriority priority,
            boolean completed) {}

    public record UnresolvedTask(
            UUID dailyPlanItemId,
            UUID roadmapItemId,
            String title,
            DailyTaskStatus status,
            int plannedMinutes) {}

    public record TopicSignal(
            UUID roadmapItemId,
            DailyTaskStatus latestStatus,
            int recentActualMinutes,
            int completionPercentage,
            SignalBand difficulty,
            SignalBand understanding,
            SuggestedFocus suggestedFocus,
            Instant lastActivityAt) {}

    public enum TopicPriority {
        WEAK,
        UNRESOLVED,
        REVIEW_DUE,
        NEXT
    }

    public enum SignalBand {
        LOW,
        MEDIUM,
        HIGH,
        UNKNOWN
    }

    public enum SuggestedFocus {
        REVIEW,
        PRACTICE,
        CONTINUE
    }
}
