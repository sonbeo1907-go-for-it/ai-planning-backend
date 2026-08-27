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
import com.codegym.aiplanning.service.evaluation.WeakTopicContextResolver;
import com.codegym.aiplanning.service.evaluation.WeakTopicContextResolver.WeakTopicPromptContext;
import com.codegym.aiplanning.service.daily.ai.DailyPlanningContext.RoadmapContext;
import com.codegym.aiplanning.service.daily.ai.DailyPlanningContext.RoadmapTopic;
import com.codegym.aiplanning.service.daily.ai.DailyPlanningContext.UnfinishedTask;
import com.codegym.aiplanning.service.daily.ai.DailyPlanningContext.WeaknessSignal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabasePlanningContextBuilder implements PlanningContextBuilder {

    private static final int MAX_PROGRESS_ENTRIES = 100;
    private static final int MAX_PROGRESS_SIGNALS = 30;

    private final DailyPlanRepository dailyPlanRepository;
    private final DailyPlanVersionRepository dailyPlanVersionRepository;
    private final DailyPlanItemRepository dailyPlanItemRepository;
    private final ProgressEntryRepository progressEntryRepository;
    private final RoadmapRepository roadmapRepository;
    private final RoadmapVersionRepository roadmapVersionRepository;
    private final RoadmapItemRepository roadmapItemRepository;
    private final WeakTopicContextResolver weakTopicContextResolver;

    public DatabasePlanningContextBuilder(
            DailyPlanRepository dailyPlanRepository,
            DailyPlanVersionRepository dailyPlanVersionRepository,
            DailyPlanItemRepository dailyPlanItemRepository,
            ProgressEntryRepository progressEntryRepository,
            RoadmapRepository roadmapRepository,
            RoadmapVersionRepository roadmapVersionRepository,
            RoadmapItemRepository roadmapItemRepository,
            WeakTopicContextResolver weakTopicContextResolver) {
        this.dailyPlanRepository = dailyPlanRepository;
        this.dailyPlanVersionRepository = dailyPlanVersionRepository;
        this.dailyPlanItemRepository = dailyPlanItemRepository;
        this.progressEntryRepository = progressEntryRepository;
        this.roadmapRepository = roadmapRepository;
        this.roadmapVersionRepository = roadmapVersionRepository;
        this.roadmapItemRepository = roadmapItemRepository;
        this.weakTopicContextResolver = weakTopicContextResolver;
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
        List<WeakTopicPromptContext> unresolvedWeakTopics = weakTopicContextResolver
                .resolveUnresolvedWeakTopics(userId, roadmap.getId());

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
                unfinishedTasks,
                progressContext.weaknessSignals(),
                unresolvedWeakTopics,
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

    private ProgressContext buildProgressContext(DailyPlan targetPlan, UUID userId) {
        List<ProgressEntry> entries = progressEntryRepository.findByUserIdOrderByRecordedAtDesc(
                userId,
                PageRequest.of(0, MAX_PROGRESS_ENTRIES));
        if (entries.isEmpty()) {
            return new ProgressContext(List.of(), List.of());
        }

        Map<UUID, DailyPlanItem> itemsById = dailyPlanItemRepository
                .findAllById(entries.stream().map(ProgressEntry::getDailyPlanItemId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(DailyPlanItem::getId, Function.identity()));
        Map<UUID, DailyPlanVersion> versionsById = dailyPlanVersionRepository
                .findAllById(itemsById.values().stream()
                        .map(DailyPlanItem::getDailyPlanVersionId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(DailyPlanVersion::getId, Function.identity()));
        Map<UUID, DailyPlan> plansById = dailyPlanRepository
                .findAllById(versionsById.values().stream()
                        .map(DailyPlanVersion::getDailyPlanId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(DailyPlan::getId, Function.identity()));

        List<ProgressSignal> signals = new ArrayList<>();
        Map<String, WeaknessSignal> weaknessByTopic = new LinkedHashMap<>();
        for (ProgressEntry entry : entries) {
            DailyPlanItem item = itemsById.get(entry.getDailyPlanItemId());
            DailyPlanVersion version = item == null
                    ? null
                    : versionsById.get(item.getDailyPlanVersionId());
            DailyPlan sourcePlan = version == null
                    ? null
                    : plansById.get(version.getDailyPlanId());
            if (!belongsToContext(sourcePlan, targetPlan)) {
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
            if (signals.size() < MAX_PROGRESS_SIGNALS) {
                signals.add(signal);
            }

            weaknessSignal(signal).ifPresent(weakness -> weaknessByTopic.putIfAbsent(
                    weaknessKey(signal),
                    weakness));
        }

        return new ProgressContext(
                List.copyOf(signals),
                List.copyOf(weaknessByTopic.values()));
    }

    private boolean belongsToContext(DailyPlan sourcePlan, DailyPlan targetPlan) {
        return sourcePlan != null
                && !sourcePlan.getId().equals(targetPlan.getId())
                && Objects.equals(sourcePlan.getRoadmapId(), targetPlan.getRoadmapId())
                && !sourcePlan.getPlanDate().isAfter(targetPlan.getPlanDate());
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

    private String weaknessKey(ProgressSignal signal) {
        UUID identifier = signal.roadmapItemId() == null
                ? signal.dailyPlanItemId()
                : signal.roadmapItemId();
        return identifier.toString();
    }

    private DailyTaskStatus toTaskStatus(ProgressEntryStatus status) {
        return DailyTaskStatus.valueOf(status.name());
    }

    private BusinessException invalidPlan(String message) {
        return new BusinessException(ErrorCode.INVALID_PLAN_TRANSITION, message);
    }

    private record ProgressContext(
            List<ProgressSignal> progressSignals,
            List<WeaknessSignal> weaknessSignals) {}
}
