package com.codegym.aiplanning.service.daily.ai;

import com.codegym.aiplanning.entity.daily.DailyTaskStatus;
import com.codegym.aiplanning.service.daily.ai.DailyPlanPromptContext.RelevantTopic;
import com.codegym.aiplanning.service.daily.ai.DailyPlanPromptContext.SignalBand;
import com.codegym.aiplanning.service.daily.ai.DailyPlanPromptContext.SuggestedFocus;
import com.codegym.aiplanning.service.daily.ai.DailyPlanPromptContext.TopicPriority;
import com.codegym.aiplanning.service.daily.ai.DailyPlanPromptContext.TopicSignal;
import com.codegym.aiplanning.service.daily.ai.DailyPlanPromptContext.UnresolvedTask;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DailyPlanPromptContextBuilder {

    static final int MAX_RELEVANT_TOPICS = 12;
    static final int MAX_WEAK_TOPICS = 7;
    static final int MAX_UNRESOLVED_TASKS = 5;
    static final int MAX_TOPIC_SIGNALS = 10;
    static final int REVIEW_COOLDOWN_DAYS = 7;

    private static final int MAX_ROADMAP_TITLE_LENGTH = 160;
    private static final int MAX_TOPIC_TITLE_LENGTH = 160;
    private static final int MAX_TASK_TITLE_LENGTH = 160;

    public DailyPlanPromptContext build(DailyPlanningContext source) {
        List<DailyPlanningContext.UnfinishedTask> selectedUnfinished = source.unfinishedTasks()
                .stream()
                .limit(MAX_UNRESOLVED_TASKS)
                .toList();

        Map<UUID, DailyPlanningContext.RoadmapTopic> activeTopics = activeTopics(source);
        Map<UUID, DailyPlanningContext.LatestTopicOutcome> latestOutcomes =
                latestOutcomes(source, activeTopics.keySet());
        Set<UUID> weakTopicIds = weakTopicIds(source, activeTopics.keySet(), latestOutcomes);
        Set<UUID> unresolvedTopicIds = unresolvedTopicIds(selectedUnfinished, activeTopics.keySet());
        Set<UUID> reviewDueTopicIds = reviewDueTopicIds(source, latestOutcomes, weakTopicIds);
        Set<UUID> nextTopicIds = nextTopicIds(activeTopics.keySet(), latestOutcomes);
        List<RelevantTopic> relevantTopics = selectRelevantTopics(
                source,
                weakTopicIds,
                unresolvedTopicIds,
                reviewDueTopicIds,
                nextTopicIds,
                latestOutcomes);

        Set<UUID> selectedTopicIds = relevantTopics.stream()
                .map(RelevantTopic::roadmapItemId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        return new DailyPlanPromptContext(
                source.targetDate(),
                source.timeZone(),
                source.availableMinutes(),
                shorten(source.roadmap().title(), MAX_ROADMAP_TITLE_LENGTH),
                relevantTopics,
                source.previousPlan() == null ? null : source.previousPlan().planDate(),
                selectedUnfinished.stream().map(this::toUnresolvedTask).toList(),
                aggregateTopicSignals(
                        source.recentProgress(),
                        latestOutcomes,
                        selectedTopicIds));
    }

    private Map<UUID, DailyPlanningContext.RoadmapTopic> activeTopics(
            DailyPlanningContext source) {
        Map<UUID, DailyPlanningContext.RoadmapTopic> topics = new LinkedHashMap<>();
        for (DailyPlanningContext.RoadmapTopic topic : source.roadmap().topics()) {
            topics.put(topic.roadmapItemId(), topic);
        }
        return topics;
    }

    private Map<UUID, DailyPlanningContext.LatestTopicOutcome> latestOutcomes(
            DailyPlanningContext source,
            Set<UUID> activeTopicIds) {
        Map<UUID, DailyPlanningContext.LatestTopicOutcome> outcomes = new LinkedHashMap<>();
        for (DailyPlanningContext.LatestTopicOutcome outcome : source.latestTopicOutcomes()) {
            if (outcome.roadmapItemId() != null
                    && activeTopicIds.contains(outcome.roadmapItemId())) {
                outcomes.put(outcome.roadmapItemId(), outcome);
            }
        }
        return outcomes;
    }

    private Set<UUID> weakTopicIds(
            DailyPlanningContext source,
            Set<UUID> activeTopicIds,
            Map<UUID, DailyPlanningContext.LatestTopicOutcome> latestOutcomes) {
        Set<UUID> weakIds = new HashSet<>();
        for (DailyPlanningContext.WeaknessSignal weakness : source.weaknessSignals()) {
            if (weakness.roadmapItemId() != null
                    && activeTopicIds.contains(weakness.roadmapItemId())
                    && !wasCompletedOnPreviousPlanDate(
                            latestOutcomes.get(weakness.roadmapItemId()),
                            source)) {
                weakIds.add(weakness.roadmapItemId());
            }
        }
        return weakIds;
    }

    private Set<UUID> reviewDueTopicIds(
            DailyPlanningContext source,
            Map<UUID, DailyPlanningContext.LatestTopicOutcome> latestOutcomes,
            Set<UUID> weakTopicIds) {
        Set<UUID> reviewDueIds = new HashSet<>();
        for (DailyPlanningContext.LatestTopicOutcome outcome : latestOutcomes.values()) {
            if (isCompleted(outcome)
                    && !weakTopicIds.contains(outcome.roadmapItemId())
                    && cooldownExpired(outcome, source)) {
                reviewDueIds.add(outcome.roadmapItemId());
            }
        }
        return reviewDueIds;
    }

    private Set<UUID> nextTopicIds(
            Set<UUID> activeTopicIds,
            Map<UUID, DailyPlanningContext.LatestTopicOutcome> latestOutcomes) {
        Set<UUID> nextIds = new HashSet<>(activeTopicIds);
        latestOutcomes.values().stream()
                .filter(this::isCompleted)
                .map(DailyPlanningContext.LatestTopicOutcome::roadmapItemId)
                .forEach(nextIds::remove);
        return nextIds;
    }

    private Set<UUID> unresolvedTopicIds(
            List<DailyPlanningContext.UnfinishedTask> unfinishedTasks,
            Set<UUID> activeTopicIds) {
        Set<UUID> unresolvedIds = new HashSet<>();
        for (DailyPlanningContext.UnfinishedTask task : unfinishedTasks) {
            if (task.roadmapItemId() != null
                    && activeTopicIds.contains(task.roadmapItemId())) {
                unresolvedIds.add(task.roadmapItemId());
            }
        }
        return unresolvedIds;
    }

    private List<RelevantTopic> selectRelevantTopics(
            DailyPlanningContext source,
            Set<UUID> weakTopicIds,
            Set<UUID> unresolvedTopicIds,
            Set<UUID> reviewDueTopicIds,
            Set<UUID> nextTopicIds,
            Map<UUID, DailyPlanningContext.LatestTopicOutcome> latestOutcomes) {
        Map<UUID, RelevantTopic> selected = new LinkedHashMap<>();

        addTopics(
                source.roadmap().topics(),
                weakTopicIds,
                TopicPriority.WEAK,
                selected,
                MAX_WEAK_TOPICS,
                latestOutcomes);
        addTopics(
                source.roadmap().topics(),
                unresolvedTopicIds,
                TopicPriority.UNRESOLVED,
                selected,
                MAX_RELEVANT_TOPICS,
                latestOutcomes);
        addTopics(
                source.roadmap().topics(),
                reviewDueTopicIds,
                TopicPriority.REVIEW_DUE,
                selected,
                MAX_RELEVANT_TOPICS,
                latestOutcomes);
        addTopics(
                source.roadmap().topics(),
                nextTopicIds,
                TopicPriority.NEXT,
                selected,
                MAX_RELEVANT_TOPICS,
                latestOutcomes);

        return List.copyOf(selected.values());
    }

    private void addTopics(
            List<DailyPlanningContext.RoadmapTopic> orderedTopics,
            Set<UUID> candidateIds,
            TopicPriority priority,
            Map<UUID, RelevantTopic> selected,
            int limit,
            Map<UUID, DailyPlanningContext.LatestTopicOutcome> latestOutcomes) {
        for (DailyPlanningContext.RoadmapTopic topic : orderedTopics) {
            if (selected.size() >= limit) {
                return;
            }
            if (!candidateIds.contains(topic.roadmapItemId())
                    || selected.containsKey(topic.roadmapItemId())) {
                continue;
            }
            selected.put(
                    topic.roadmapItemId(),
                    new RelevantTopic(
                            topic.roadmapItemId(),
                            shorten(topic.title(), MAX_TOPIC_TITLE_LENGTH),
                            topic.estimatedMinutes(),
                            priority,
                            isCompleted(latestOutcomes.get(topic.roadmapItemId()))));
        }
    }

    private UnresolvedTask toUnresolvedTask(DailyPlanningContext.UnfinishedTask source) {
        return new UnresolvedTask(
                source.dailyPlanItemId(),
                source.roadmapItemId(),
                shorten(source.title(), MAX_TASK_TITLE_LENGTH),
                source.status(),
                source.plannedMinutes());
    }

    private List<TopicSignal> aggregateTopicSignals(
            List<DailyPlanningContext.ProgressSignal> progress,
            Map<UUID, DailyPlanningContext.LatestTopicOutcome> latestOutcomes,
            Set<UUID> selectedTopicIds) {
        Map<UUID, Integer> recentMinutes = new LinkedHashMap<>();
        for (DailyPlanningContext.ProgressSignal entry : progress) {
            UUID topicId = entry.roadmapItemId();
            if (topicId == null || !selectedTopicIds.contains(topicId)) {
                continue;
            }
            recentMinutes.merge(topicId, Math.max(0, entry.actualMinutes()), Integer::sum);
        }

        List<TopicSignal> result = new ArrayList<>();
        for (UUID topicId : selectedTopicIds) {
            if (result.size() >= MAX_TOPIC_SIGNALS) {
                break;
            }
            DailyPlanningContext.LatestTopicOutcome latest = latestOutcomes.get(topicId);
            if (latest != null) {
                result.add(toTopicSignal(
                        latest,
                        recentMinutes.getOrDefault(topicId, 0)));
            }
        }
        return List.copyOf(result);
    }

    private TopicSignal toTopicSignal(
            DailyPlanningContext.LatestTopicOutcome latest,
            int recentActualMinutes) {
        return new TopicSignal(
                latest.roadmapItemId(),
                latest.status(),
                recentActualMinutes,
                latest.completionPercentage(),
                difficultyBand(latest.difficulty()),
                understandingBand(latest.understandingRating()),
                suggestedFocus(latest),
                latest.recordedAt());
    }

    private SignalBand difficultyBand(Integer difficulty) {
        if (difficulty == null) {
            return SignalBand.UNKNOWN;
        }
        if (difficulty >= 4) {
            return SignalBand.HIGH;
        }
        if (difficulty == 3) {
            return SignalBand.MEDIUM;
        }
        return SignalBand.LOW;
    }

    private SignalBand understandingBand(Integer understanding) {
        if (understanding == null) {
            return SignalBand.UNKNOWN;
        }
        if (understanding >= 4) {
            return SignalBand.HIGH;
        }
        if (understanding == 3) {
            return SignalBand.MEDIUM;
        }
        return SignalBand.LOW;
    }

    private SuggestedFocus suggestedFocus(DailyPlanningContext.LatestTopicOutcome latest) {
        if (latest.understandingRating() != null && latest.understandingRating() <= 2) {
            return SuggestedFocus.REVIEW;
        }
        if (latest.status() == DailyTaskStatus.PARTIALLY_COMPLETED
                || latest.status() == DailyTaskStatus.SKIPPED) {
            return SuggestedFocus.REVIEW;
        }
        if (latest.difficulty() != null && latest.difficulty() >= 4) {
            return SuggestedFocus.PRACTICE;
        }
        return SuggestedFocus.CONTINUE;
    }

    private String shorten(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private boolean isCompleted(DailyPlanningContext.LatestTopicOutcome outcome) {
        return outcome != null
                && (outcome.status() == DailyTaskStatus.COMPLETED
                        || outcome.completionPercentage() >= 100);
    }

    private boolean wasCompletedOnPreviousPlanDate(
            DailyPlanningContext.LatestTopicOutcome outcome,
            DailyPlanningContext source) {
        if (outcome == null || !isCompleted(outcome) || outcome.planDate() == null) {
            return false;
        }
        return outcome.planDate().equals(source.targetDate().minusDays(1));
    }

    private boolean cooldownExpired(
            DailyPlanningContext.LatestTopicOutcome outcome,
            DailyPlanningContext source) {
        if (outcome.planDate() == null || wasCompletedOnPreviousPlanDate(outcome, source)) {
            return false;
        }
        return ChronoUnit.DAYS.between(outcome.planDate(), source.targetDate())
                >= REVIEW_COOLDOWN_DAYS;
    }
}
