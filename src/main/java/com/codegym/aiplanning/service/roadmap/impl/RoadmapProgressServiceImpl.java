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
import com.codegym.aiplanning.repository.daily.ProgressEntryRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapItemProgressRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapItemRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapRepository;
import com.codegym.aiplanning.service.roadmap.RoadmapProgressService;
import com.codegym.aiplanning.service.roadmap.progress.LearningUnitProgressProjection;
import com.codegym.aiplanning.service.roadmap.progress.LearningUnitProgressProjector;
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
    private final ProgressEntryRepository progressEntryRepository;
    private final LearningUnitProgressProjector progressProjector;

    public RoadmapProgressServiceImpl(
            RoadmapRepository roadmapRepository,
            RoadmapItemRepository roadmapItemRepository,
            RoadmapItemProgressRepository progressRepository,
            ProgressEntryRepository progressEntryRepository) {
        this.roadmapRepository = roadmapRepository;
        this.roadmapItemRepository = roadmapItemRepository;
        this.progressRepository = progressRepository;
        this.progressEntryRepository = progressEntryRepository;
        this.progressProjector = new LearningUnitProgressProjector();
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
        if (item.getItemType() != RoadmapItemType.LEARNING_UNIT) {
            throw new BusinessException(
                    ErrorCode.INVALID_PLAN_TRANSITION,
                    "Daily Plan outcomes can only record progress against a Learning Unit.");
        }

        rebuildLearningUnitSnapshot(userId, item);
    }

    @Override
    @Transactional
    public void correctOutcome(
            UUID userId,
            UUID roadmapItemId,
            ProgressEntry progressEntry,
            ProgressEntryStatus outcome) {
        RoadmapItem item = requireOwnedLearningUnit(userId, roadmapItemId);
        rebuildLearningUnitSnapshot(userId, item);
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

    private RoadmapItem requireOwnedLearningUnit(UUID userId, UUID roadmapItemId) {
        RoadmapItem item = roadmapItemRepository.findOwnedById(roadmapItemId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "The Learning Unit linked to this progress entry no longer exists."));
        if (item.getItemType() != RoadmapItemType.LEARNING_UNIT) {
            throw new BusinessException(
                    ErrorCode.INVALID_PLAN_TRANSITION,
                    "Progress corrections can only target a Learning Unit.");
        }
        return item;
    }

    private void rebuildLearningUnitSnapshot(UUID userId, RoadmapItem unit) {
        RoadmapItemProgress progress = progressRepository
                .findByUserIdAndRoadmapItemId(userId, unit.getId())
                .orElseGet(() -> RoadmapItemProgress.create(
                        userId,
                        unit.getRoadmapVersion().getId(),
                        unit.getId()));
        List<ProgressEntry> history = progressEntryRepository
                .findByUserIdAndLearningUnitIdOrderByRecordedAtAscIdAsc(
                        userId, unit.getId());
        LearningUnitProgressProjection projection = progressProjector.project(
                history, progress);
        progress.replaceProjection(
                projection.status(),
                projection.latestOutcome(),
                projection.completionPercentage(),
                projection.lastProgressEntryId(),
                projection.completedAt());
        progressRepository.saveAndFlush(progress);
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

        int percentage = units.isEmpty()
                ? 0
                : (int) Math.round(completedUnits * 100.0 / units.size());
        boolean hasRecordedProgress = unitResponses.stream()
                .anyMatch(unit -> unit.status() != RoadmapItemProgressStatus.NOT_STARTED
                        || unit.latestOutcome() != null);
        RoadmapItemProgressStatus status = percentage == 100
                ? RoadmapItemProgressStatus.COMPLETED
                : hasRecordedProgress
                        ? RoadmapItemProgressStatus.IN_PROGRESS
                        : RoadmapItemProgressStatus.NOT_STARTED;

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
                progress == null ? null : progress.getLatestOutcome(),
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

}
