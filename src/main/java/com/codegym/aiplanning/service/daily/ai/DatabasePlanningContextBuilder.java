package com.codegym.aiplanning.service.daily.ai;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.entity.daily.DailyPlan;
import com.codegym.aiplanning.entity.daily.DailyPlanItem;
import com.codegym.aiplanning.entity.daily.DailyPlanVersion;
import com.codegym.aiplanning.entity.daily.DailyPlanVersionStatus;
import com.codegym.aiplanning.entity.daily.DailyTaskStatus;
import com.codegym.aiplanning.entity.daily.ProgressEntry;
import com.codegym.aiplanning.entity.daily.ProgressEntryStatus;
import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapItem;
import com.codegym.aiplanning.entity.roadmap.RoadmapItemType;
import com.codegym.aiplanning.entity.roadmap.RoadmapStatus;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersion;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersionStatus;
import com.codegym.aiplanning.repository.daily.DailyPlanItemRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanVersionRepository;
import com.codegym.aiplanning.repository.daily.ProgressEntryRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapItemRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapVersionRepository;
import com.codegym.aiplanning.service.daily.ai.DailyPlanningContext.PreviousPlan;
import com.codegym.aiplanning.service.daily.ai.DailyPlanningContext.ProgressSignal;
import com.codegym.aiplanning.service.daily.ai.DailyPlanningContext.LatestTopicOutcome;
import com.codegym.aiplanning.service.daily.ai.DailyPlanningContext.RoadmapContext;
import com.codegym.aiplanning.service.daily.ai.DailyPlanningContext.RoadmapTopic;
import com.codegym.aiplanning.service.daily.ai.DailyPlanningContext.UnfinishedTask;
import com.codegym.aiplanning.service.daily.ai.DailyPlanningContext.WeaknessSignal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabasePlanningContextBuilder implements PlanningContextBuilder {

    private static final int MAX_PROGRESS_SIGNALS = 30;

    private final DailyPlanRepository dailyPlanRepository;
    private final DailyPlanVersionRepository dailyPlanVersionRepository;
    private final DailyPlanItemRepository dailyPlanItemRepository;
    private final ProgressEntryRepository progressEntryRepository;
    private final RoadmapRepository roadmapRepository;
    private final RoadmapVersionRepository roadmapVersionRepository;
    private final RoadmapItemRepository roadmapItemRepository;

    public DatabasePlanningContextBuilder(
            DailyPlanRepository dailyPlanRepository,
            DailyPlanVersionRepository dailyPlanVersionRepository,
            DailyPlanItemRepository dailyPlanItemRepository,
            ProgressEntryRepository progressEntryRepository,
            RoadmapRepository roadmapRepository,
            RoadmapVersionRepository roadmapVersionRepository,
            RoadmapItemRepository roadmapItemRepository) {
        this.dailyPlanRepository = dailyPlanRepository;
        this.dailyPlanVersionRepository = dailyPlanVersionRepository;
        this.dailyPlanItemRepository = dailyPlanItemRepository;
        this.progressEntryRepository = progressEntryRepository;
        this.roadmapRepository = roadmapRepository;
        this.roadmapVersionRepository = roadmapVersionRepository;
        this.roadmapItemRepository = roadmapItemRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public DailyPlanningContext buildContext(UUID dailyPlanId, UUID userId) {
        DailyPlan plan = dailyPlanRepository.findByIdAndUserId(dailyPlanId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.DAILY_PLAN_NOT_FOUND,
                        "Daily plan not found."));
        if (plan.getRoadmapId() == null) {
            throw invalidPlan("Select a Roadmap before generating a Daily Plan with AI.");
        }

        Roadmap roadmap = roadmapRepository.findByIdAndOwnerId(plan.getRoadmapId(), userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Roadmap not found."));
        if (roadmap.getStatus() != RoadmapStatus.ACTIVE || roadmap.getActiveVersionId() == null) {
            throw invalidPlan("The selected Roadmap needs an ACTIVE version.");
        }

        RoadmapVersion activeRoadmapVersion = roadmapVersionRepository
                .findByIdAndRoadmapId(roadmap.getActiveVersionId(), roadmap.getId())
                .filter(version -> version.getStatus() == RoadmapVersionStatus.ACTIVE)
                .orElseThrow(() -> invalidPlan("The selected Roadmap ACTIVE version is invalid."));

        DailyPlanVersion planningVersion = dailyPlanVersionRepository
                .findByDailyPlanIdAndStatus(dailyPlanId, DailyPlanVersionStatus.DRAFT)
                .or(() -> dailyPlanVersionRepository
                        .findTopByDailyPlanIdOrderByVersionNumberDesc(dailyPlanId))
                .orElseThrow(() -> invalidPlan("The Daily Plan has no version to provide an available-time budget."));

        RoadmapContext roadmapContext = buildRoadmapContext(roadmap, activeRoadmapVersion);
        ProgressContext progressContext = buildProgressContext(plan, userId);
        PreviousPlan previousPlan = buildPreviousPlan(plan, userId).orElse(null);

        List<UnfinishedTask> unfinishedTasks = previousPlan == null
                ? List.of()
                : previousPlan.unfinishedTasks();

        return new DailyPlanningContext(
                plan.getId(),
                userId,
                plan.getPlanDate(),
                plan.getTimeZoneSnapshot(),
                planningVersion.getAvailableMinutes(),
                roadmapContext,
                progressContext.progressSignals(),
                progressContext.latestTopicOutcomes(),
                unfinishedTasks,
                progressContext.weaknessSignals(),
                previousPlan);
    }

    private RoadmapContext buildRoadmapContext(
            Roadmap roadmap, RoadmapVersion activeVersion) {
        List<RoadmapItem> items = roadmapItemRepository
                .findAllByRoadmapVersionIdOrderByOrderIndexAsc(activeVersion.getId());
        Map<UUID, RoadmapItem> milestones = items.stream()
                .filter(item -> item.getItemType() == RoadmapItemType.MILESTONE)
                .collect(Collectors.toMap(
                        RoadmapItem::getId,
                        Function.identity()));

        List<RoadmapTopic> topics = items.stream()
                .filter(item -> item.getItemType() == RoadmapItemType.TOPIC)
                .sorted(Comparator
                        .comparingInt((RoadmapItem item) -> milestoneOrder(item, milestones))
                        .thenComparingInt(RoadmapItem::getOrderIndex))
                .map(item -> {
                    RoadmapItem milestone = item.getParent() == null
                            ? null
                            : milestones.get(item.getParent().getId());
                    return new RoadmapTopic(
                            item.getId(),
                            milestone == null ? null : milestone.getId(),
                            milestone == null ? null : milestone.getTitle(),
                            item.getTitle(),
                            item.getDescription(),
                            item.getEstimatedMinutes(),
                            item.getOrderIndex());
                })
                .toList();

        if (topics.isEmpty()) {
            throw invalidPlan("The selected Roadmap ACTIVE version has no topics.");
        }

        return new RoadmapContext(
                roadmap.getId(),
                activeVersion.getId(),
                activeVersion.getVersionNumber(),
                roadmap.getTitle(),
                roadmap.getDescription(),
                topics);
    }

    private int milestoneOrder(
            RoadmapItem topic,
            Map<UUID, RoadmapItem> milestones) {
        if (topic.getParent() == null) {
            return Integer.MAX_VALUE;
        }
        RoadmapItem milestone = milestones.get(topic.getParent().getId());
        return milestone == null ? Integer.MAX_VALUE : milestone.getOrderIndex();
    }

    private ProgressContext buildProgressContext(DailyPlan targetPlan, UUID userId) {
        List<DailyPlan> sourcePlans = dailyPlanRepository
                .findByUserIdAndRoadmapIdAndPlanDateBeforeOrderByPlanDateDesc(
                        userId,
                        targetPlan.getRoadmapId(),
                        targetPlan.getPlanDate());
        if (sourcePlans.isEmpty()) {
            return ProgressContext.empty();
        }

        Map<UUID, DailyPlan> plansById = sourcePlans.stream()
                .collect(Collectors.toMap(DailyPlan::getId, Function.identity()));
        List<DailyPlanVersion> versions = dailyPlanVersionRepository.findByDailyPlanIdIn(
                sourcePlans.stream().map(DailyPlan::getId).toList());
        if (versions.isEmpty()) {
            return ProgressContext.empty();
        }

        Map<UUID, DailyPlanVersion> versionsById = versions.stream()
                .collect(Collectors.toMap(DailyPlanVersion::getId, Function.identity()));
        List<DailyPlanItem> items = dailyPlanItemRepository.findByDailyPlanVersionIds(
                versions.stream().map(DailyPlanVersion::getId).toList());
        if (items.isEmpty()) {
            return ProgressContext.empty();
        }

        Map<UUID, DailyPlanItem> itemsById = items.stream()
                .collect(Collectors.toMap(DailyPlanItem::getId, Function.identity()));
        List<ProgressEntry> entries = progressEntryRepository
                .findByUserIdAndDailyPlanItemIdInOrderByRecordedAtDesc(
                        userId,
                        items.stream().map(DailyPlanItem::getId).toList());

        List<ProgressSignal> scopedSignals = new ArrayList<>();
        for (ProgressEntry entry : entries) {
            DailyPlanItem item = itemsById.get(entry.getDailyPlanItemId());
            DailyPlanVersion version = item == null
                    ? null
                    : versionsById.get(item.getDailyPlanVersionId());
            DailyPlan sourcePlan = version == null
                    ? null
                    : plansById.get(version.getDailyPlanId());
            if (sourcePlan == null) {
                continue;
            }

            DailyTaskStatus status = toTaskStatus(entry.getStatus());
            ProgressSignal signal = new ProgressSignal(
                    entry.getId(),
                    item.getId(),
                    item.getRoadmapItemId(),
                    item.getTitle(),
                    status,
                    item.getPlannedMinutes(),
                    entry.getActualMinutes(),
                    entry.getCompletionPercentage(),
                    entry.getDifficulty(),
                    entry.getUnderstandingRating(),
                    entry.getActualResult(),
                    entry.getRecordedAt());
            scopedSignals.add(signal);
        }

        List<ProgressSignal> orderedSignals = orderByRecordedAtDesc(scopedSignals);
        return new ProgressContext(
                orderedSignals.stream().limit(MAX_PROGRESS_SIGNALS).toList(),
                deriveLatestTopicOutcomes(orderedSignals, itemsById, versionsById, plansById),
                deriveWeaknessSignals(orderedSignals));
    }

    List<LatestTopicOutcome> deriveLatestTopicOutcomes(
            List<ProgressSignal> signals,
            Map<UUID, DailyPlanItem> itemsById,
            Map<UUID, DailyPlanVersion> versionsById,
            Map<UUID, DailyPlan> plansById) {
        Map<UUID, LatestTopicOutcome> latestByRoadmapItem = new LinkedHashMap<>();
        for (ProgressSignal signal : orderByRecordedAtDesc(signals)) {
            if (signal.roadmapItemId() == null
                    || latestByRoadmapItem.containsKey(signal.roadmapItemId())) {
                continue;
            }

            DailyPlanItem item = itemsById.get(signal.dailyPlanItemId());
            DailyPlanVersion version = item == null
                    ? null
                    : versionsById.get(item.getDailyPlanVersionId());
            DailyPlan plan = version == null
                    ? null
                    : plansById.get(version.getDailyPlanId());
            latestByRoadmapItem.put(
                    signal.roadmapItemId(),
                    new LatestTopicOutcome(
                            signal.roadmapItemId(),
                            signal.status(),
                            signal.completionPercentage(),
                            signal.difficulty(),
                            signal.understandingRating(),
                            signal.recordedAt(),
                            plan == null ? null : plan.getPlanDate()));
        }
        return List.copyOf(latestByRoadmapItem.values());
    }

    /**
     * Determines weakness from the most recent outcome for each Roadmap Item.
     *
     * <p>An earlier difficult, skipped, or partially-completed attempt must not override a later
     * successful attempt with healthy self-assessment. Manual Daily Plan tasks without a Roadmap
     * Item remain available as unfinished-task context, but cannot establish roadmap-topic weakness.
     */
    List<WeaknessSignal> deriveWeaknessSignals(List<ProgressSignal> signals) {
        Map<UUID, ProgressSignal> latestByRoadmapItem = new LinkedHashMap<>();
        for (ProgressSignal signal : orderByRecordedAtDesc(signals)) {
            if (signal.roadmapItemId() != null) {
                latestByRoadmapItem.putIfAbsent(signal.roadmapItemId(), signal);
            }
        }

        return latestByRoadmapItem.values().stream()
                .map(this::weaknessSignal)
                .flatMap(Optional::stream)
                .toList();
    }

    private List<ProgressSignal> orderByRecordedAtDesc(List<ProgressSignal> signals) {
        return signals.stream()
                .sorted(Comparator.comparing(
                        ProgressSignal::recordedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private Optional<PreviousPlan> buildPreviousPlan(DailyPlan targetPlan, UUID userId) {
        return dailyPlanRepository
                .findFirstByUserIdAndRoadmapIdAndPlanDateBeforeOrderByPlanDateDesc(
                        userId,
                        targetPlan.getRoadmapId(),
                        targetPlan.getPlanDate())
                .flatMap(plan -> currentVersion(plan).map(version -> {
                    List<UnfinishedTask> unfinished = dailyPlanItemRepository
                            .findByDailyPlanVersionIdOrderByOrderIndexAsc(version.getId())
                            .stream()
                            .filter(item -> item.getStatus() != DailyTaskStatus.COMPLETED)
                            .map(this::toUnfinishedTask)
                            .toList();
                    return new PreviousPlan(
                            plan.getId(),
                            plan.getPlanDate(),
                            version.getId(),
                            unfinished);
                }));
    }

    private Optional<DailyPlanVersion> currentVersion(DailyPlan plan) {
        if (plan.getActiveVersionId() != null) {
            return dailyPlanVersionRepository.findByIdAndDailyPlanId(
                    plan.getActiveVersionId(),
                    plan.getId());
        }
        return dailyPlanVersionRepository.findTopByDailyPlanIdOrderByVersionNumberDesc(
                plan.getId());
    }

    private UnfinishedTask toUnfinishedTask(DailyPlanItem item) {
        return new UnfinishedTask(
                item.getId(),
                item.getRoadmapItemId(),
                item.getTitle(),
                item.getDescription(),
                item.getStatus(),
                item.getPlannedMinutes());
    }

    private Optional<WeaknessSignal> weaknessSignal(ProgressSignal signal) {
        List<String> reasons = new ArrayList<>();
        if (signal.difficulty() != null && signal.difficulty() >= 4) {
            reasons.add("reported difficulty is high");
        }
        if (signal.understandingRating() != null && signal.understandingRating() <= 2) {
            reasons.add("understanding rating is low");
        }
        if (signal.status() == DailyTaskStatus.PARTIALLY_COMPLETED) {
            reasons.add("task was partially completed");
        }
        if (signal.status() == DailyTaskStatus.SKIPPED) {
            reasons.add("task was skipped");
        }
        if (reasons.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new WeaknessSignal(
                signal.roadmapItemId(),
                signal.title(),
                signal.difficulty(),
                signal.understandingRating(),
                signal.status(),
                String.join("; ", reasons)));
    }

    private DailyTaskStatus toTaskStatus(ProgressEntryStatus status) {
        return DailyTaskStatus.valueOf(status.name());
    }

    private BusinessException invalidPlan(String message) {
        return new BusinessException(ErrorCode.INVALID_PLAN_TRANSITION, message);
    }

    private record ProgressContext(
            List<ProgressSignal> progressSignals,
            List<LatestTopicOutcome> latestTopicOutcomes,
            List<WeaknessSignal> weaknessSignals) {

        private static ProgressContext empty() {
            return new ProgressContext(List.of(), List.of(), List.of());
        }
    }
}
