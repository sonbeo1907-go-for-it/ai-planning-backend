package com.codegym.aiplanning.service.daily.step.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.daily.dto.CreateTaskStepRequest;
import com.codegym.aiplanning.controller.daily.dto.DailyPlanTaskStepsResponse;
import com.codegym.aiplanning.controller.daily.dto.UpdateTaskStepCompletionRequest;
import com.codegym.aiplanning.controller.daily.dto.UpdateTaskStepRequest;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.daily.DailyPlan;
import com.codegym.aiplanning.entity.daily.DailyPlanItem;
import com.codegym.aiplanning.entity.daily.DailyPlanStatus;
import com.codegym.aiplanning.entity.daily.DailyPlanTaskStep;
import com.codegym.aiplanning.entity.daily.DailyPlanTaskStepState;
import com.codegym.aiplanning.entity.daily.DailyPlanVersion;
import com.codegym.aiplanning.entity.daily.DailyPlanVersionStatus;
import com.codegym.aiplanning.entity.daily.DailyTaskStatus;
import com.codegym.aiplanning.repository.daily.DailyPlanItemRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanTaskStepRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanTaskStepStateRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanVersionRepository;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.service.daily.step.DailyPlanTaskStepService;
import com.codegym.aiplanning.service.daily.step.TaskStepOrderNormalizer;
import com.codegym.aiplanning.service.daily.step.TaskStepReadModelBuilder;
import com.codegym.aiplanning.service.daily.step.TaskStepValidator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyPlanTaskStepServiceImpl implements DailyPlanTaskStepService {

    private static final int TEMPORARY_ORDER_OFFSET = 100_000;

    private final DailyPlanRepository dailyPlanRepository;
    private final DailyPlanVersionRepository dailyPlanVersionRepository;
    private final DailyPlanItemRepository dailyPlanItemRepository;
    private final DailyPlanTaskStepRepository taskStepRepository;
    private final DailyPlanTaskStepStateRepository taskStepStateRepository;
    private final TaskStepValidator validator;
    private final TaskStepOrderNormalizer orderNormalizer;
    private final TaskStepReadModelBuilder readModelBuilder;
    private final AuditLogService auditLogService;

    public DailyPlanTaskStepServiceImpl(
            DailyPlanRepository dailyPlanRepository,
            DailyPlanVersionRepository dailyPlanVersionRepository,
            DailyPlanItemRepository dailyPlanItemRepository,
            DailyPlanTaskStepRepository taskStepRepository,
            DailyPlanTaskStepStateRepository taskStepStateRepository,
            TaskStepValidator validator,
            TaskStepOrderNormalizer orderNormalizer,
            TaskStepReadModelBuilder readModelBuilder,
            AuditLogService auditLogService) {
        this.dailyPlanRepository = dailyPlanRepository;
        this.dailyPlanVersionRepository = dailyPlanVersionRepository;
        this.dailyPlanItemRepository = dailyPlanItemRepository;
        this.taskStepRepository = taskStepRepository;
        this.taskStepStateRepository = taskStepStateRepository;
        this.validator = validator;
        this.orderNormalizer = orderNormalizer;
        this.readModelBuilder = readModelBuilder;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional(readOnly = true)
    public DailyPlanTaskStepsResponse getSteps(
            UUID planId,
            UUID versionId,
            UUID itemId,
            Jwt actorJwt) {
        UUID userId = extractUserId(actorJwt);
        requireOwnedItem(planId, versionId, itemId, userId);
        return buildResponse(itemId);
    }

    @Override
    @Transactional
    public DailyPlanTaskStepsResponse createStep(
            UUID planId,
            UUID versionId,
            UUID itemId,
            CreateTaskStepRequest request,
            Jwt actorJwt) {
        UUID userId = extractUserId(actorJwt);
        DailyPlanVersion version = requireOwnedVersionForUpdate(
                planId,
                versionId,
                userId);
        requireDraft(version);
        DailyPlanItem item = requireOwnedItem(planId, versionId, itemId, userId);

        List<DailyPlanTaskStep> steps = new ArrayList<>(
                taskStepRepository.findByDailyPlanItemIdOrderByOrderIndex(itemId));
        int targetIndex = request.orderIndex() == null
                ? steps.size()
                : Math.min(request.orderIndex(), steps.size());

        DailyPlanTaskStep step = DailyPlanTaskStep.create(
                itemId,
                validator.normalizeRequiredTitle(request.title()),
                validator.normalizeOptionalGuidance(request.guidance()),
                steps.size(),
                request.estimatedMinutes(),
                request.required() != null ? request.required() : true);
        DailyPlanTaskStep savedStep = taskStepRepository.saveAndFlush(step);

        steps.add(targetIndex, savedStep);
        orderNormalizer.normalize(steps);
        validator.validateAll(steps, item.getPlannedMinutes(), item.getTitle());
        persistNormalizedOrder(steps);

        audit(userId, actorJwt, AuditEventAction.TASK_STEP_CREATED, savedStep.getId());
        return buildResponse(itemId);
    }

    @Override
    @Transactional
    public DailyPlanTaskStepsResponse updateStep(
            UUID planId,
            UUID versionId,
            UUID itemId,
            UUID stepId,
            UpdateTaskStepRequest request,
            Jwt actorJwt) {
        UUID userId = extractUserId(actorJwt);
        DailyPlanVersion version = requireOwnedVersionForUpdate(
                planId,
                versionId,
                userId);
        requireDraft(version);
        DailyPlanItem item = requireOwnedItem(planId, versionId, itemId, userId);
        DailyPlanTaskStep step = requireOwnedStep(
                planId,
                versionId,
                itemId,
                stepId,
                userId);
        requireExpectedVersion(step, request.entityVersion());

        List<DailyPlanTaskStep> steps = new ArrayList<>(
                taskStepRepository.findByDailyPlanItemIdOrderByOrderIndex(itemId));
        steps.removeIf(candidate -> candidate.getId().equals(stepId));
        int targetIndex = Math.min(request.orderIndex(), steps.size());

        step.updateDraftDetails(
                validator.normalizeRequiredTitle(request.title()),
                validator.normalizeOptionalGuidance(request.guidance()),
                step.getOrderIndex(),
                request.estimatedMinutes(),
                request.required());
        steps.add(targetIndex, step);
        orderNormalizer.normalize(steps);
        validator.validateAll(steps, item.getPlannedMinutes(), item.getTitle());
        persistNormalizedOrder(steps);

        audit(userId, actorJwt, AuditEventAction.TASK_STEP_UPDATED, stepId);
        return buildResponse(itemId);
    }

    @Override
    @Transactional
    public DailyPlanTaskStepsResponse deleteStep(
            UUID planId,
            UUID versionId,
            UUID itemId,
            UUID stepId,
            long expectedEntityVersion,
            Jwt actorJwt) {
        UUID userId = extractUserId(actorJwt);
        DailyPlanVersion version = requireOwnedVersionForUpdate(
                planId,
                versionId,
                userId);
        requireDraft(version);
        DailyPlanItem item = requireOwnedItem(planId, versionId, itemId, userId);
        DailyPlanTaskStep step = requireOwnedStep(
                planId,
                versionId,
                itemId,
                stepId,
                userId);
        requireExpectedVersion(step, expectedEntityVersion);

        List<DailyPlanTaskStep> remaining = new ArrayList<>(
                taskStepRepository.findByDailyPlanItemIdOrderByOrderIndex(itemId));
        remaining.removeIf(candidate -> candidate.getId().equals(stepId));
        validator.validateAll(remaining, item.getPlannedMinutes(), item.getTitle());

        // Do not dirty surviving order indexes before the removed row is gone. Hibernate
        // flushes entity updates before deletes, which would otherwise collide with the
        // unique (daily_plan_item_id, order_index) constraint.
        taskStepRepository.delete(step);
        taskStepRepository.flush();
        persistNormalizedOrder(remaining);

        audit(userId, actorJwt, AuditEventAction.TASK_STEP_DELETED, stepId);
        return buildResponse(itemId);
    }

    @Override
    @Transactional
    public DailyPlanTaskStepsResponse setCompletion(
            UUID planId,
            UUID versionId,
            UUID itemId,
            UUID stepId,
            UpdateTaskStepCompletionRequest request,
            Jwt actorJwt) {
        UUID userId = extractUserId(actorJwt);
        DailyPlanTaskStep step = taskStepRepository.findOwnedByPathForUpdate(
                        planId,
                        versionId,
                        itemId,
                        stepId,
                        userId)
                .orElseThrow(this::stepNotFound);
        DailyPlan plan = dailyPlanRepository.findByIdAndUserId(planId, userId)
                .orElseThrow(this::stepNotFound);
        if (plan.getActiveVersionId() == null
                || !plan.getActiveVersionId().equals(versionId)
                || (plan.getStatus() != DailyPlanStatus.READY
                        && plan.getStatus() != DailyPlanStatus.IN_PROGRESS)) {
            throw new BusinessException(
                    ErrorCode.DAILY_PLAN_VERSION_NOT_ACTIVE,
                    "Task Step completion can be changed only while the Daily Plan is executable.");
        }
        DailyPlanVersion version = dailyPlanVersionRepository
                .findByIdAndDailyPlanId(versionId, planId)
                .orElseThrow(this::stepNotFound);
        if (version.getStatus() != DailyPlanVersionStatus.ACTIVE) {
            throw new BusinessException(
                    ErrorCode.DAILY_PLAN_VERSION_NOT_ACTIVE,
                    "Task Step completion can be changed only in the ACTIVE Daily Plan version.");
        }

        DailyPlanItem item = dailyPlanItemRepository.findById(itemId)
                .filter(candidate -> candidate.getDailyPlanVersionId().equals(versionId))
                .filter(candidate -> !candidate.isRemoved())
                .orElseThrow(this::stepNotFound);
        if (isTerminal(item.getStatus())) {
            throw new BusinessException(
                    ErrorCode.DAILY_PLAN_LOCKED,
                    "Task Step completion cannot change after the parent task has a final outcome.");
        }

        if (request.completed() == null) {
            throw new BusinessException(
                    ErrorCode.TASK_STEP_INVALID,
                    "Task Step completion value is required.");
        }
        boolean requestedCompletion = request.completed();

        DailyPlanTaskStepState state = taskStepStateRepository
                .findByTaskStepId(step.getId())
                .orElse(null);
        if (state == null && !requestedCompletion) {
            return buildResponse(itemId);
        }
        if (state != null && Boolean.valueOf(requestedCompletion).equals(state.getCompleted())) {
            return buildResponse(itemId);
        }
        requireExpectedStateVersion(state, request.stateVersion());
        if (state == null) {
            state = DailyPlanTaskStepState.create(step.getId());
        }
        state.setCompleted(requestedCompletion, Instant.now());
        taskStepStateRepository.saveAndFlush(state);

        audit(
                userId,
                actorJwt,
                AuditEventAction.TASK_STEP_COMPLETION_CHANGED,
                stepId);
        return buildResponse(itemId);
    }

    private DailyPlanVersion requireOwnedVersionForUpdate(
            UUID planId,
            UUID versionId,
            UUID userId) {
        dailyPlanRepository.findByIdAndUserId(planId, userId)
                .orElseThrow(this::stepNotFound);
        return dailyPlanVersionRepository
                .findByIdAndDailyPlanIdForUpdate(versionId, planId)
                .orElseThrow(this::stepNotFound);
    }

    private DailyPlanItem requireOwnedItem(
            UUID planId,
            UUID versionId,
            UUID itemId,
            UUID userId) {
        DailyPlan plan = dailyPlanRepository.findByIdAndUserId(planId, userId)
                .orElseThrow(this::stepNotFound);
        DailyPlanVersion version = dailyPlanVersionRepository
                .findByIdAndDailyPlanId(versionId, plan.getId())
                .orElseThrow(this::stepNotFound);
        return dailyPlanItemRepository.findById(itemId)
                .filter(item -> item.getDailyPlanVersionId().equals(version.getId()))
                .filter(item -> !item.isRemoved())
                .orElseThrow(this::stepNotFound);
    }

    private DailyPlanTaskStep requireOwnedStep(
            UUID planId,
            UUID versionId,
            UUID itemId,
            UUID stepId,
            UUID userId) {
        return taskStepRepository.findOwnedByPath(
                        planId,
                        versionId,
                        itemId,
                        stepId,
                        userId)
                .orElseThrow(this::stepNotFound);
    }

    private void requireDraft(DailyPlanVersion version) {
        if (version.getStatus() != DailyPlanVersionStatus.DRAFT) {
            throw new BusinessException(
                    ErrorCode.DAILY_PLAN_VERSION_NOT_EDITABLE,
                    "Task Step content can be modified only in a DRAFT Daily Plan version.");
        }
    }

    private void requireExpectedVersion(
            DailyPlanTaskStep step,
            Long expectedEntityVersion) {
        if (expectedEntityVersion == null || step.getVersion() != expectedEntityVersion) {
            throw new BusinessException(
                    ErrorCode.CONCURRENT_MODIFICATION,
                    "The Task Step was modified by another request. Reload it and try again.");
        }
    }

    private void requireExpectedStateVersion(
            DailyPlanTaskStepState state,
            Long expectedStateVersion) {
        if (state == null) {
            if (expectedStateVersion != null) {
                throw new BusinessException(
                        ErrorCode.CONCURRENT_MODIFICATION,
                        "The Task Step state changed. Reload it and try again.");
            }
            return;
        }
        if (expectedStateVersion == null || state.getVersion() != expectedStateVersion) {
            throw new BusinessException(
                    ErrorCode.CONCURRENT_MODIFICATION,
                    "The Task Step state changed. Reload it and try again.");
        }
    }

    private void persistNormalizedOrder(List<DailyPlanTaskStep> steps) {
        for (int index = 0; index < steps.size(); index++) {
            steps.get(index).updateOrderIndex(TEMPORARY_ORDER_OFFSET + index);
        }
        taskStepRepository.saveAllAndFlush(steps);

        orderNormalizer.normalize(steps);
        taskStepRepository.saveAllAndFlush(steps);
    }

    private DailyPlanTaskStepsResponse buildResponse(UUID itemId) {
        return readModelBuilder.buildForItem(itemId);
    }

    private boolean isTerminal(DailyTaskStatus status) {
        return status == DailyTaskStatus.COMPLETED
                || status == DailyTaskStatus.PARTIALLY_COMPLETED
                || status == DailyTaskStatus.SKIPPED;
    }

    private void audit(
            UUID userId,
            Jwt actorJwt,
            AuditEventAction action,
            UUID stepId) {
        auditLogService.logAction(
                userId,
                extractUsername(actorJwt),
                action,
                "DailyPlanTaskStep",
                stepId.toString());
    }

    private UUID extractUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new BusinessException(
                    ErrorCode.AUTHENTICATION_REQUIRED,
                    "Authentication is invalid.");
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.AUTHENTICATION_REQUIRED,
                    "Authentication is invalid.");
        }
    }

    private String extractUsername(Jwt jwt) {
        if (jwt == null) {
            return "system";
        }
        String username = jwt.getClaimAsString("preferred_username");
        if (username == null || username.isBlank()) {
            username = jwt.getClaimAsString("email");
        }
        if (username == null || username.isBlank()) {
            username = jwt.getSubject();
        }
        return username != null ? username : "unknown";
    }

    private BusinessException stepNotFound() {
        return new BusinessException(
                ErrorCode.RESOURCE_NOT_FOUND,
                "Task Step was not found.");
    }
}
