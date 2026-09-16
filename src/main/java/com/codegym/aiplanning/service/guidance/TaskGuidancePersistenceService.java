package com.codegym.aiplanning.service.guidance;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.entity.ai.AiExecutionOperation;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.guidance.TaskGuidance;
import com.codegym.aiplanning.entity.guidance.TaskGuidanceReference;
import com.codegym.aiplanning.entity.guidance.TaskGuidanceRevision;
import com.codegym.aiplanning.entity.guidance.TaskGuidanceRevisionStatus;
import com.codegym.aiplanning.entity.guidance.TaskStepGuidance;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.repository.guidance.TaskGuidanceReferenceRepository;
import com.codegym.aiplanning.repository.guidance.TaskGuidanceRepository;
import com.codegym.aiplanning.repository.guidance.TaskGuidanceRevisionRepository;
import com.codegym.aiplanning.repository.guidance.TaskStepGuidanceRepository;
import com.codegym.aiplanning.service.guidance.model.GeneratedTaskGuidance;
import com.codegym.aiplanning.service.guidance.model.GeneratedTaskGuidance.GeneratedReference;
import com.codegym.aiplanning.service.guidance.model.GeneratedTaskGuidance.GeneratedStepGuidance;
import com.codegym.aiplanning.service.guidance.model.TaskGuidanceContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskGuidancePersistenceService {

    private final TaskGuidanceRepository taskGuidanceRepository;
    private final TaskGuidanceRevisionRepository revisionRepository;
    private final TaskStepGuidanceRepository stepGuidanceRepository;
    private final TaskGuidanceReferenceRepository referenceRepository;
    private final UserAccountRepository userAccountRepository;

    public TaskGuidancePersistenceService(
            TaskGuidanceRepository taskGuidanceRepository,
            TaskGuidanceRevisionRepository revisionRepository,
            TaskStepGuidanceRepository stepGuidanceRepository,
            TaskGuidanceReferenceRepository referenceRepository,
            UserAccountRepository userAccountRepository) {
        this.taskGuidanceRepository = taskGuidanceRepository;
        this.revisionRepository = revisionRepository;
        this.stepGuidanceRepository = stepGuidanceRepository;
        this.referenceRepository = referenceRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional
    public UUID persist(
            UUID executionId,
            TaskGuidanceContext context,
            GeneratedTaskGuidance generated,
            AiExecutionOperation operation) {
        TaskGuidanceRevision alreadyPersisted = revisionRepository
                .findByAiExecutionId(executionId)
                .orElse(null);
        if (alreadyPersisted != null) {
            requireSameGenerationTarget(
                    alreadyPersisted,
                    context.ownerId(),
                    context.dailyPlanId(),
                    context.dailyPlanVersionId(),
                    context.dailyPlanItemId());
            return alreadyPersisted.getId();
        }

        TaskGuidance root = taskGuidanceRepository
                .findOwnedByItemIdForUpdate(
                        context.dailyPlanItemId(),
                        context.ownerId())
                .orElseGet(() -> createRoot(context));

        int revisionNumber = revisionRepository
                .findFirstByTaskGuidanceIdOrderByRevisionNumberDesc(root.getId())
                .map(existing -> existing.getRevisionNumber() + 1)
                .orElse(1);
        if (operation == AiExecutionOperation.GENERATE && revisionNumber > 1) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "Task Guidance already exists; use regeneration.");
        }

        TaskGuidanceRevision previousDraft = revisionRepository
                .findFirstByTaskGuidanceIdAndStatusOrderByRevisionNumberDesc(
                        root.getId(),
                        TaskGuidanceRevisionStatus.DRAFT)
                .orElse(null);
        if (previousDraft != null) {
            previousDraft.supersede();
            revisionRepository.saveAndFlush(previousDraft);
        }

        TaskGuidanceRevision revision = revisionRepository.saveAndFlush(
                TaskGuidanceRevision.draft(
                        root,
                        executionId,
                        revisionNumber,
                        generated.objective(),
                        generated.taskSummary(),
                        context.dailyPlanItemEntityVersion(),
                        context.fingerprint(),
                        Instant.now()));

        Map<UUID, TaskStepGuidance> persistedByStepId = persistStepGuidances(
                revision,
                context,
                generated.stepGuidances());
        persistReferences(
                revision,
                null,
                generated.references());
        for (GeneratedStepGuidance step : generated.stepGuidances()) {
            persistReferences(
                    revision,
                    persistedByStepId.get(step.taskStepId()),
                    step.references());
        }
        return revision.getId();
    }

    @Transactional(readOnly = true)
    public Optional<UUID> findExistingResult(
            UUID executionId,
            UUID ownerId,
            UUID dailyPlanItemId) {
        return revisionRepository.findByAiExecutionId(executionId)
                .map(revision -> {
                    TaskGuidance root = revision.getTaskGuidance();
                    boolean sameExecutionTarget = root.getOwner()
                                    .getId()
                                    .equals(ownerId)
                            && root.getDailyPlanItemId().equals(dailyPlanItemId);
                    if (!sameExecutionTarget) {
                        throw new BusinessException(
                                ErrorCode.CONFLICT,
                                "AI execution is already associated with another "
                                        + "Task Guidance target.");
                    }
                    return revision.getId();
                });
    }

    private void requireSameGenerationTarget(
            TaskGuidanceRevision revision,
            UUID ownerId,
            UUID dailyPlanId,
            UUID dailyPlanVersionId,
            UUID dailyPlanItemId) {
        TaskGuidance root = revision.getTaskGuidance();
        boolean sameTarget = root.getOwner().getId().equals(ownerId)
                && root.getDailyPlanId().equals(dailyPlanId)
                && root.getDailyPlanVersionId().equals(dailyPlanVersionId)
                && root.getDailyPlanItemId().equals(dailyPlanItemId);
        if (!sameTarget) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "AI execution is already associated with another Task Guidance target.");
        }
    }

    private TaskGuidance createRoot(TaskGuidanceContext context) {
        UserAccount owner = userAccountRepository.findById(context.ownerId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.AUTHENTICATION_REQUIRED,
                        "The authenticated account is unavailable."));
        return taskGuidanceRepository.saveAndFlush(TaskGuidance.create(
                owner,
                context.dailyPlanId(),
                context.dailyPlanVersionId(),
                context.dailyPlanItemId()));
    }

    private Map<UUID, TaskStepGuidance> persistStepGuidances(
            TaskGuidanceRevision revision,
            TaskGuidanceContext context,
            List<GeneratedStepGuidance> generatedSteps) {
        Map<UUID, GeneratedStepGuidance> generatedById = new HashMap<>();
        generatedSteps.forEach(step -> generatedById.put(step.taskStepId(), step));

        List<TaskStepGuidance> entities = new ArrayList<>();
        for (TaskGuidanceContext.TaskStepSnapshot snapshot : context.taskSteps()) {
            GeneratedStepGuidance generated = generatedById.get(snapshot.id());
            entities.add(TaskStepGuidance.create(
                    revision,
                    snapshot.id(),
                    snapshot.entityVersion(),
                    snapshot.orderIndex(),
                    generated.instructions(),
                    generated.expectedResult(),
                    generated.tips(),
                    generated.cautions(),
                    generated.prerequisites()));
        }

        Map<UUID, TaskStepGuidance> persistedByStepId = new HashMap<>();
        stepGuidanceRepository.saveAllAndFlush(entities)
                .forEach(entity -> persistedByStepId.put(
                        entity.getSourceTaskStepId(),
                        entity));
        return persistedByStepId;
    }

    private void persistReferences(
            TaskGuidanceRevision revision,
            TaskStepGuidance stepGuidance,
            List<GeneratedReference> generatedReferences) {
        List<TaskGuidanceReference> entities = new ArrayList<>();
        for (int index = 0; index < generatedReferences.size(); index++) {
            GeneratedReference generated = generatedReferences.get(index);
            entities.add(toEntity(
                    revision,
                    stepGuidance,
                    generated,
                    index));
        }
        referenceRepository.saveAll(entities);
    }

    private TaskGuidanceReference toEntity(
            TaskGuidanceRevision revision,
            TaskStepGuidance stepGuidance,
            GeneratedReference reference,
            int orderIndex) {
        return switch (reference.provenance()) {
            case MATERIAL -> TaskGuidanceReference.material(
                    revision,
                    stepGuidance,
                    reference.targetId(),
                    reference.displayLabel(),
                    reference.locator(),
                    orderIndex);
            case LEARNING_SOURCE -> TaskGuidanceReference.learningSource(
                    revision,
                    stepGuidance,
                    reference.targetId(),
                    reference.displayLabel(),
                    reference.locator(),
                    orderIndex);
            case ROADMAP_CONTEXT -> TaskGuidanceReference.roadmapContext(
                    revision,
                    stepGuidance,
                    reference.targetId(),
                    reference.displayLabel(),
                    reference.locator(),
                    orderIndex);
            case UNVERIFIED_EXTERNAL -> TaskGuidanceReference.unverifiedExternal(
                    revision,
                    stepGuidance,
                    reference.externalUrl(),
                    reference.displayLabel(),
                    orderIndex);
        };
    }
}
