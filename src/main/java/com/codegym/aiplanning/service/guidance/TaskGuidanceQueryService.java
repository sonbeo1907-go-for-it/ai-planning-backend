package com.codegym.aiplanning.service.guidance;

import com.codegym.aiplanning.common.api.PageResponse;
import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.guidance.dto.TaskGuidanceOverviewResponse;
import com.codegym.aiplanning.controller.guidance.dto.TaskGuidanceReferenceResponse;
import com.codegym.aiplanning.controller.guidance.dto.TaskGuidanceRevisionResponse;
import com.codegym.aiplanning.controller.guidance.dto.TaskGuidanceRevisionSummaryResponse;
import com.codegym.aiplanning.controller.guidance.dto.TaskStepGuidanceResponse;
import com.codegym.aiplanning.entity.daily.DailyPlanItem;
import com.codegym.aiplanning.entity.daily.DailyPlanTaskStep;
import com.codegym.aiplanning.entity.guidance.TaskGuidance;
import com.codegym.aiplanning.entity.guidance.TaskGuidanceReference;
import com.codegym.aiplanning.entity.guidance.TaskGuidanceRevision;
import com.codegym.aiplanning.entity.guidance.TaskGuidanceRevisionStatus;
import com.codegym.aiplanning.entity.guidance.TaskStepGuidance;
import com.codegym.aiplanning.repository.daily.DailyPlanTaskStepRepository;
import com.codegym.aiplanning.repository.guidance.TaskGuidanceReferenceRepository;
import com.codegym.aiplanning.repository.guidance.TaskGuidanceRepository;
import com.codegym.aiplanning.repository.guidance.TaskGuidanceRevisionRepository;
import com.codegym.aiplanning.repository.guidance.TaskStepGuidanceRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskGuidanceQueryService {

    private static final List<TaskGuidanceRevisionStatus> VISIBLE_STATUSES =
            List.of(
                    TaskGuidanceRevisionStatus.DRAFT,
                    TaskGuidanceRevisionStatus.SUPERSEDED);

    private final TaskGuidanceContextBuilder contextBuilder;
    private final TaskGuidanceRepository taskGuidanceRepository;
    private final TaskGuidanceRevisionRepository revisionRepository;
    private final TaskStepGuidanceRepository stepGuidanceRepository;
    private final TaskGuidanceReferenceRepository referenceRepository;
    private final DailyPlanTaskStepRepository taskStepRepository;

    public TaskGuidanceQueryService(
            TaskGuidanceContextBuilder contextBuilder,
            TaskGuidanceRepository taskGuidanceRepository,
            TaskGuidanceRevisionRepository revisionRepository,
            TaskStepGuidanceRepository stepGuidanceRepository,
            TaskGuidanceReferenceRepository referenceRepository,
            DailyPlanTaskStepRepository taskStepRepository) {
        this.contextBuilder = contextBuilder;
        this.taskGuidanceRepository = taskGuidanceRepository;
        this.revisionRepository = revisionRepository;
        this.stepGuidanceRepository = stepGuidanceRepository;
        this.referenceRepository = referenceRepository;
        this.taskStepRepository = taskStepRepository;
    }

    @Transactional(readOnly = true)
    public TaskGuidanceOverviewResponse getOverview(
            UUID ownerId,
            UUID dailyPlanId,
            UUID dailyPlanVersionId,
            UUID dailyPlanItemId,
            Pageable pageable) {
        DailyPlanItem currentItem = contextBuilder.requireOwnedItem(
                ownerId,
                dailyPlanId,
                dailyPlanVersionId,
                dailyPlanItemId);
        TaskGuidance root = requireRoot(ownerId, dailyPlanItemId);
        List<DailyPlanTaskStep> currentSteps = taskStepRepository
                .findByDailyPlanItemIdOrderByOrderIndex(dailyPlanItemId);

        TaskGuidanceRevision latest = latestVisible(root.getId());
        Page<TaskGuidanceRevision> revisionPage = revisionRepository
                .findByTaskGuidanceIdAndStatusInOrderByRevisionNumberDesc(
                        root.getId(),
                        VISIBLE_STATUSES,
                        pageable);
        List<UUID> revisionIds = new ArrayList<>(revisionPage.getContent().stream()
                .map(TaskGuidanceRevision::getId)
                .toList());
        if (!revisionIds.contains(latest.getId())) {
            revisionIds.add(latest.getId());
        }
        Map<UUID, List<TaskStepGuidance>> snapshotsByRevision =
                groupSnapshots(stepGuidanceRepository
                        .findByRevisionIdInOrderByRevisionIdAscOrderIndexAsc(
                                revisionIds));
        TaskGuidanceRevisionResponse latestResponse = toResponse(
                root,
                latest,
                latest.getId(),
                currentItem,
                currentSteps,
                snapshotsByRevision.getOrDefault(latest.getId(), List.of()));
        Page<TaskGuidanceRevisionSummaryResponse> summaries = revisionPage
                .map(revision -> toSummary(
                        revision,
                        latest.getId(),
                        currentItem,
                        currentSteps,
                        snapshotsByRevision.getOrDefault(
                                revision.getId(),
                                List.of())));
        return new TaskGuidanceOverviewResponse(
                root.getId(),
                root.getDailyPlanVersionId(),
                root.getDailyPlanItemId(),
                latestResponse,
                PageResponse.from(summaries));
    }

    @Transactional(readOnly = true)
    public TaskGuidanceRevisionResponse getRevision(
            UUID ownerId,
            UUID dailyPlanId,
            UUID dailyPlanVersionId,
            UUID dailyPlanItemId,
            UUID revisionId) {
        DailyPlanItem currentItem = contextBuilder.requireOwnedItem(
                ownerId,
                dailyPlanId,
                dailyPlanVersionId,
                dailyPlanItemId);
        TaskGuidance root = requireRoot(ownerId, dailyPlanItemId);
        TaskGuidanceRevision revision = revisionRepository
                .findByIdAndTaskGuidanceId(revisionId, root.getId())
                .filter(candidate -> candidate.getStatus()
                        != TaskGuidanceRevisionStatus.ARCHIVED)
                .orElseThrow(this::notFound);
        TaskGuidanceRevision latest = latestVisible(root.getId());
        List<DailyPlanTaskStep> currentSteps = taskStepRepository
                .findByDailyPlanItemIdOrderByOrderIndex(dailyPlanItemId);
        return toResponse(
                root,
                revision,
                latest.getId(),
                currentItem,
                currentSteps,
                stepGuidanceRepository.findByRevisionIdOrderByOrderIndex(
                        revision.getId()));
    }

    private TaskGuidanceRevisionResponse toResponse(
            TaskGuidance root,
            TaskGuidanceRevision revision,
            UUID latestRevisionId,
            DailyPlanItem currentItem,
            List<DailyPlanTaskStep> currentSteps,
            List<TaskStepGuidance> stepGuidances) {
        List<TaskGuidanceReference> references = referenceRepository
                .findByRevisionIdOrderByOrderIndex(revision.getId());
        Map<UUID, List<TaskGuidanceReferenceResponse>> referencesByStep =
                new HashMap<>();
        List<TaskGuidanceReferenceResponse> taskReferences = new ArrayList<>();
        for (TaskGuidanceReference reference : references) {
            TaskGuidanceReferenceResponse response = toReferenceResponse(reference);
            if (reference.getTaskStepGuidance() == null) {
                taskReferences.add(response);
            } else {
                referencesByStep
                        .computeIfAbsent(
                                reference.getTaskStepGuidance().getId(),
                                ignored -> new ArrayList<>())
                        .add(response);
            }
        }

        List<TaskStepGuidanceResponse> stepResponses = stepGuidances.stream()
                .map(step -> new TaskStepGuidanceResponse(
                        step.getId(),
                        step.getSourceTaskStepId(),
                        step.getTaskStepEntityVersion(),
                        step.getOrderIndex(),
                        step.getInstructions(),
                        step.getExpectedResult(),
                        step.getTips(),
                        step.getCautions(),
                        step.getPrerequisites(),
                        List.copyOf(referencesByStep.getOrDefault(
                                step.getId(),
                                List.of()))))
                .toList();
        return new TaskGuidanceRevisionResponse(
                root.getId(),
                revision.getId(),
                root.getDailyPlanVersionId(),
                root.getDailyPlanItemId(),
                revision.getRevisionNumber(),
                revision.getStatus(),
                revision.getId().equals(latestRevisionId),
                isStale(revision, stepGuidances, currentItem, currentSteps),
                revision.getObjective(),
                revision.getTaskSummary(),
                stepResponses,
                List.copyOf(taskReferences),
                revision.getGeneratedAt());
    }

    private TaskGuidanceRevisionSummaryResponse toSummary(
            TaskGuidanceRevision revision,
            UUID latestRevisionId,
            DailyPlanItem currentItem,
            List<DailyPlanTaskStep> currentSteps,
            List<TaskStepGuidance> snapshots) {
        return new TaskGuidanceRevisionSummaryResponse(
                revision.getId(),
                revision.getRevisionNumber(),
                revision.getStatus(),
                revision.getId().equals(latestRevisionId),
                isStale(revision, snapshots, currentItem, currentSteps),
                revision.getObjective(),
                revision.getGeneratedAt());
    }

    private Map<UUID, List<TaskStepGuidance>> groupSnapshots(
            List<TaskStepGuidance> snapshots) {
        Map<UUID, List<TaskStepGuidance>> result = new HashMap<>();
        for (TaskStepGuidance snapshot : snapshots) {
            result.computeIfAbsent(
                            snapshot.getRevision().getId(),
                            ignored -> new ArrayList<>())
                    .add(snapshot);
        }
        return result;
    }

    private boolean isStale(
            TaskGuidanceRevision revision,
            List<TaskStepGuidance> snapshots,
            DailyPlanItem currentItem,
            List<DailyPlanTaskStep> currentSteps) {
        if (revision.getDailyPlanItemEntityVersion() != currentItem.getVersion()
                || snapshots.size() != currentSteps.size()) {
            return true;
        }
        for (int index = 0; index < snapshots.size(); index++) {
            TaskStepGuidance snapshot = snapshots.get(index);
            DailyPlanTaskStep current = currentSteps.get(index);
            if (!snapshot.getSourceTaskStepId().equals(current.getId())
                    || snapshot.getTaskStepEntityVersion() != current.getVersion()
                    || !snapshot.getOrderIndex().equals(current.getOrderIndex())) {
                return true;
            }
        }
        return false;
    }

    private TaskGuidanceReferenceResponse toReferenceResponse(
            TaskGuidanceReference reference) {
        UUID targetId = switch (reference.getProvenance()) {
            case MATERIAL -> reference.getMaterialId();
            case LEARNING_SOURCE -> reference.getLearningSourceId();
            case ROADMAP_CONTEXT -> reference.getRoadmapItemId();
            case UNVERIFIED_EXTERNAL -> null;
        };
        return new TaskGuidanceReferenceResponse(
                reference.getId(),
                reference.getProvenance(),
                reference.getDisplayLabel(),
                reference.getLocator(),
                targetId,
                reference.getExternalUrl(),
                reference.isUnverified());
    }

    private TaskGuidance requireRoot(UUID ownerId, UUID dailyPlanItemId) {
        return taskGuidanceRepository
                .findByDailyPlanItemIdAndOwnerId(dailyPlanItemId, ownerId)
                .orElseThrow(this::notFound);
    }

    private TaskGuidanceRevision latestVisible(UUID rootId) {
        return revisionRepository
                .findByTaskGuidanceIdAndStatusInOrderByRevisionNumberDesc(
                        rootId,
                        VISIBLE_STATUSES)
                .stream()
                .findFirst()
                .orElseThrow(this::notFound);
    }

    private BusinessException notFound() {
        return new BusinessException(
                ErrorCode.TASK_GUIDANCE_NOT_FOUND,
                "Task Guidance was not found.");
    }
}
