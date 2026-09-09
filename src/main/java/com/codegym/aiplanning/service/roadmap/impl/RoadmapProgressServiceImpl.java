package com.codegym.aiplanning.service.roadmap.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.roadmap.dto.RoadmapProgressResponse;
import com.codegym.aiplanning.controller.roadmap.dto.RoadmapProgressResponse.LearningUnitProgress;
import com.codegym.aiplanning.controller.roadmap.dto.RoadmapProgressResponse.TopicProgress;
import com.codegym.aiplanning.entity.daily.ProgressEntry;
import com.codegym.aiplanning.entity.daily.ProgressEntryStatus;
import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapItem;
import com.codegym.aiplanning.entity.roadmap.RoadmapItemProgress;
import com.codegym.aiplanning.entity.roadmap.RoadmapItemProgressStatus;
import com.codegym.aiplanning.entity.roadmap.RoadmapItemType;
import com.codegym.aiplanning.repository.roadmap.RoadmapItemProgressRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapItemRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapRepository;
import com.codegym.aiplanning.service.roadmap.RoadmapProgressService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoadmapProgressServiceImpl implements RoadmapProgressService {

    private final RoadmapRepository roadmapRepository;
    private final RoadmapItemRepository roadmapItemRepository;
    private final RoadmapItemProgressRepository progressRepository;

    public RoadmapProgressServiceImpl(
            RoadmapRepository roadmapRepository,
            RoadmapItemRepository roadmapItemRepository,
            RoadmapItemProgressRepository progressRepository) {
        this.roadmapRepository = roadmapRepository;
        this.roadmapItemRepository = roadmapItemRepository;
        this.progressRepository = progressRepository;
    }

    @Override
    @Transactional
    public void recordOutcome(
            UUID userId,
            UUID roadmapItemId,
            ProgressEntry progressEntry,
            ProgressEntryStatus outcome) {
        if (roadmapItemId == null) {
            return;
        }

        RoadmapItem item = roadmapItemRepository.findOwnedById(roadmapItemId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "The Roadmap item linked to this task no longer exists."));
        if (item.getItemType() == RoadmapItemType.MILESTONE) {
            throw new BusinessException(
                    ErrorCode.INVALID_PLAN_TRANSITION,
                    "Daily Plan tasks cannot record progress against a Milestone.");
        }

        RoadmapItemProgress progress = progressRepository
                .findByUserIdAndRoadmapItemId(userId, item.getId())
                .orElseGet(() -> RoadmapItemProgress.create(
                        userId,
                        item.getRoadmapVersion().getId(),
                        item.getId()));
        applyOutcome(progress, progressEntry, outcome);
        progressRepository.saveAndFlush(progress);

        if (item.getItemType() == RoadmapItemType.LEARNING_UNIT) {
            updateParentTopic(userId, item, progressEntry);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public RoadmapProgressResponse getProgress(UUID userId, UUID roadmapId) {
        Roadmap roadmap = roadmapRepository.findByIdAndOwnerId(roadmapId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Roadmap resource was not found."));
        if (roadmap.getActiveVersionId() == null) {
            return new RoadmapProgressResponse(
                    roadmap.getId(), null, 0, 0, 0.0, List.of());
        }

        UUID versionId = roadmap.getActiveVersionId();
        List<RoadmapItem> items = roadmapItemRepository
                .findAllByRoadmapVersionIds(List.of(versionId));
        Map<UUID, Integer> milestoneOrderById = milestoneOrderById(items);
        Map<UUID, RoadmapItemProgress> progressByItemId = progressByItemId(
                progressRepository.findByUserIdAndRoadmapVersionId(userId, versionId));
        Map<UUID, List<RoadmapItem>> unitsByTopicId = unitsByTopicId(items);

        List<RoadmapItem> topics = items.stream()
                .filter(item -> item.getItemType() == RoadmapItemType.TOPIC)
                .sorted(Comparator
                        .comparingInt((RoadmapItem topic) -> milestoneOrder(
                                topic, milestoneOrderById))
                        .thenComparingInt(RoadmapItem::getOrderIndex))
                .toList();
        List<TopicProgress> topicResponses = new ArrayList<>();
        int completedTopics = 0;
        for (RoadmapItem topic : topics) {
            TopicProgress response = topicProgress(
                    topic,
                    unitsByTopicId.getOrDefault(topic.getId(), List.of()),
                    progressByItemId);
            topicResponses.add(response);
            if (response.status() == RoadmapItemProgressStatus.COMPLETED) {
                completedTopics++;
            }
        }

        double roadmapPercentage = topics.isEmpty()
                ? 0.0
                : completedTopics * 100.0 / topics.size();
        return new RoadmapProgressResponse(
                roadmap.getId(),
                versionId,
                completedTopics,
                topics.size(),
                roadmapPercentage,
                List.copyOf(topicResponses));
    }

    private void applyOutcome(
            RoadmapItemProgress progress,
            ProgressEntry entry,
            ProgressEntryStatus outcome) {
        if (outcome == ProgressEntryStatus.COMPLETED) {
            progress.markCompleted(
                    entry.getId(), completionTime(entry));
        } else if (outcome == ProgressEntryStatus.PARTIALLY_COMPLETED) {
            progress.markInProgress(entry.getId());
        } else {
            progress.recordSkipped(entry.getId());
        }
    }

    private void updateParentTopic(
            UUID userId, RoadmapItem unit, ProgressEntry latestEntry) {
        RoadmapItem topic = unit.getParent();
        if (topic == null || topic.getItemType() != RoadmapItemType.TOPIC) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "A Learning Unit is not attached to a valid parent Topic.");
        }

        List<RoadmapItem> units = roadmapItemRepository
                .findAllByRoadmapVersionIdAndParentIdOrderByOrderIndexAsc(
                        topic.getRoadmapVersion().getId(), topic.getId())
                .stream()
                .filter(candidate -> candidate.getItemType() == RoadmapItemType.LEARNING_UNIT)
                .toList();
        List<RoadmapItemProgress> snapshots = units.isEmpty()
                ? List.of()
                : progressRepository.findByUserIdAndRoadmapItemIdIn(
                        userId, units.stream().map(RoadmapItem::getId).toList());
        long completedUnits = snapshots.stream()
                .filter(snapshot -> snapshot.getStatus()
                        == RoadmapItemProgressStatus.COMPLETED)
                .count();
        int percentage = units.isEmpty()
                ? 0
                : (int) Math.round(completedUnits * 100.0 / units.size());

        RoadmapItemProgress topicProgress = progressRepository
                .findByUserIdAndRoadmapItemId(userId, topic.getId())
                .orElseGet(() -> RoadmapItemProgress.create(
                        userId,
                        topic.getRoadmapVersion().getId(),
                        topic.getId()));
        topicProgress.applyAggregate(
                percentage,
                latestEntry.getId(),
                completionTime(latestEntry));
        progressRepository.saveAndFlush(topicProgress);
    }

    private TopicProgress topicProgress(
            RoadmapItem topic,
            List<RoadmapItem> units,
            Map<UUID, RoadmapItemProgress> progressByItemId) {
        List<LearningUnitProgress> unitResponses = units.stream()
                .sorted(Comparator.comparingInt(RoadmapItem::getOrderIndex))
                .map(unit -> learningUnitProgress(unit, progressByItemId.get(unit.getId())))
                .toList();
        int completedUnits = (int) unitResponses.stream()
                .filter(unit -> unit.status() == RoadmapItemProgressStatus.COMPLETED)
                .count();

        RoadmapItemProgress storedTopicProgress = progressByItemId.get(topic.getId());
        int percentage;
        RoadmapItemProgressStatus status;
        if (units.isEmpty()) {
            percentage = storedTopicProgress == null
                    ? 0
                    : storedTopicProgress.getCompletionPercentage();
            status = storedTopicProgress == null
                    ? RoadmapItemProgressStatus.NOT_STARTED
                    : storedTopicProgress.getStatus();
        } else {
            percentage = (int) Math.round(completedUnits * 100.0 / units.size());
            status = percentage == 100
                    ? RoadmapItemProgressStatus.COMPLETED
                    : percentage == 0
                            ? RoadmapItemProgressStatus.NOT_STARTED
                            : RoadmapItemProgressStatus.IN_PROGRESS;
        }

        return new TopicProgress(
                topic.getId(),
                topic.getTitle(),
                status,
                percentage,
                completedUnits,
                units.size(),
                unitResponses);
    }

    private LearningUnitProgress learningUnitProgress(
            RoadmapItem unit, RoadmapItemProgress progress) {
        return new LearningUnitProgress(
                unit.getId(),
                unit.getTitle(),
                progress == null
                        ? RoadmapItemProgressStatus.NOT_STARTED
                        : progress.getStatus(),
                progress == null ? 0 : progress.getCompletionPercentage());
    }

    private Map<UUID, RoadmapItemProgress> progressByItemId(
            List<RoadmapItemProgress> progress) {
        Map<UUID, RoadmapItemProgress> result = new HashMap<>();
        for (RoadmapItemProgress snapshot : progress) {
            result.put(snapshot.getRoadmapItemId(), snapshot);
        }
        return result;
    }

    private Map<UUID, List<RoadmapItem>> unitsByTopicId(List<RoadmapItem> items) {
        Map<UUID, List<RoadmapItem>> result = new HashMap<>();
        for (RoadmapItem item : items) {
            if (item.getItemType() == RoadmapItemType.LEARNING_UNIT
                    && item.getParent() != null) {
                result.computeIfAbsent(
                                item.getParent().getId(), ignored -> new ArrayList<>())
                        .add(item);
            }
        }
        return result;
    }

    private Map<UUID, Integer> milestoneOrderById(List<RoadmapItem> items) {
        Map<UUID, Integer> result = new HashMap<>();
        for (RoadmapItem item : items) {
            if (item.getItemType() == RoadmapItemType.MILESTONE) {
                result.put(item.getId(), item.getOrderIndex());
            }
        }
        return result;
    }

    private int milestoneOrder(
            RoadmapItem topic, Map<UUID, Integer> milestoneOrderById) {
        if (topic.getParent() == null) {
            return Integer.MAX_VALUE;
        }
        return milestoneOrderById.getOrDefault(
                topic.getParent().getId(), Integer.MAX_VALUE);
    }

    private Instant completionTime(ProgressEntry entry) {
        return entry.getRecordedAt() == null ? Instant.now() : entry.getRecordedAt();
    }
}
