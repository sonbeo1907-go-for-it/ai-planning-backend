package com.codegym.aiplanning.service.ai.execution;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.ai.dto.AiExecutionResponse;
import com.codegym.aiplanning.entity.ai.AiExecution;
import com.codegym.aiplanning.entity.ai.AiExecutionInput;
import com.codegym.aiplanning.entity.ai.AiExecutionOperation;
import com.codegym.aiplanning.entity.ai.AiExecutionStatus;
import com.codegym.aiplanning.entity.ai.AiExecutionTargetType;
import com.codegym.aiplanning.entity.ai.AiProviderConfig;
import com.codegym.aiplanning.entity.ai.AiPurpose;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.daily.DailyPlan;
import com.codegym.aiplanning.entity.daily.DailyPlanStatus;
import com.codegym.aiplanning.repository.ai.AiExecutionInputRepository;
import com.codegym.aiplanning.repository.ai.AiExecutionRepository;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanRepository;
import com.codegym.aiplanning.service.ai.AiProviderSelector;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.service.roadmap.impl.AiRoadmapPersistenceService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiExecutionServiceImpl implements AiExecutionService {

    private static final List<AiExecutionStatus> ACTIVE_STATUSES =
            List.of(AiExecutionStatus.QUEUED, AiExecutionStatus.RUNNING);

    private final AiExecutionRepository executionRepository;
    private final AiExecutionInputRepository inputRepository;
    private final UserAccountRepository userAccountRepository;
    private final AiProviderSelector providerSelector;
    private final AiRoadmapPersistenceService roadmapPersistenceService;
    private final DailyPlanRepository dailyPlanRepository;
    private final AuditLogService auditLogService;

    public AiExecutionServiceImpl(
            AiExecutionRepository executionRepository,
            AiExecutionInputRepository inputRepository,
            UserAccountRepository userAccountRepository,
            AiProviderSelector providerSelector,
            AiRoadmapPersistenceService roadmapPersistenceService,
            DailyPlanRepository dailyPlanRepository,
            AuditLogService auditLogService) {
        this.executionRepository = executionRepository;
        this.inputRepository = inputRepository;
        this.userAccountRepository = userAccountRepository;
        this.providerSelector = providerSelector;
        this.roadmapPersistenceService = roadmapPersistenceService;
        this.dailyPlanRepository = dailyPlanRepository;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    public AiExecutionResponse submitRoadmapGeneration(
            UUID ownerId,
            UUID roadmapId,
            List<UUID> materialIds,
            String idempotencyKey) {
        return submit(
                ownerId,
                roadmapId,
                materialIds == null ? List.of() : materialIds,
                null,
                AiExecutionOperation.GENERATE,
                idempotencyKey);
    }

    @Override
    @Transactional
    public AiExecutionResponse submitRoadmapRegeneration(
            UUID ownerId,
            UUID roadmapId,
            String adjustmentPrompt,
            String idempotencyKey) {
        return submit(
                ownerId,
                roadmapId,
                List.of(),
                normalizePrompt(adjustmentPrompt),
                AiExecutionOperation.REGENERATE,
                idempotencyKey);
    }

    @Override
    @Transactional(readOnly = true)
    public AiExecutionResponse getOwnedExecution(UUID ownerId, UUID executionId) {
        return AiExecutionResponse.from(executionRepository
                .findByIdAndOwnerId(executionId, ownerId)
                .orElseThrow(this::notFound));
    }

    @Override
    @Transactional(readOnly = true)
    public AiExecutionResponse getLatestRoadmapExecution(
            UUID ownerId, UUID roadmapId) {
        return AiExecutionResponse.from(executionRepository
                .findFirstByOwnerIdAndTargetTypeAndTargetIdAndPurposeOrderByCreatedAtDesc(
                        ownerId,
                        AiExecutionTargetType.ROADMAP,
                        roadmapId,
                        AiPurpose.ROADMAP_GENERATION)
                .orElseThrow(this::notFound));
    }

    @Override
    @Transactional
    public AiExecutionResponse submitDailyPlanGeneration(
            UUID ownerId, UUID dailyPlanId, String idempotencyKey) {
        return submitDailyPlan(
                ownerId,
                dailyPlanId,
                AiExecutionOperation.GENERATE,
                idempotencyKey);
    }

    @Override
    @Transactional
    public AiExecutionResponse submitDailyPlanRegeneration(
            UUID ownerId, UUID dailyPlanId, String idempotencyKey) {
        return submitDailyPlan(
                ownerId,
                dailyPlanId,
                AiExecutionOperation.REGENERATE,
                idempotencyKey);
    }

    @Override
    @Transactional(readOnly = true)
    public AiExecutionResponse getLatestDailyPlanExecution(
            UUID ownerId, UUID dailyPlanId) {
        return AiExecutionResponse.from(executionRepository
                .findFirstByOwnerIdAndTargetTypeAndTargetIdAndPurposeOrderByCreatedAtDesc(
                        ownerId,
                        AiExecutionTargetType.DAILY_PLAN,
                        dailyPlanId,
                        AiPurpose.DAILY_PLAN_GENERATION)
                .orElseThrow(this::notFound));
    }

    private AiExecutionResponse submit(
            UUID ownerId,
            UUID roadmapId,
            List<UUID> materialIds,
            String adjustmentPrompt,
            AiExecutionOperation operation,
            String idempotencyKey) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        if (normalizedKey != null) {
            AiExecution existing = findMatchingIdempotentExecution(
                    ownerId,
                    normalizedKey,
                    AiExecutionTargetType.ROADMAP,
                    roadmapId,
                    AiPurpose.ROADMAP_GENERATION,
                    operation);
            if (existing != null) {
                return AiExecutionResponse.from(existing);
            }
        }

        roadmapPersistenceService.prepare(ownerId, roadmapId, materialIds);

        AiExecution active = executionRepository
                .findFirstByOwnerIdAndTargetTypeAndTargetIdAndPurposeAndStatusInOrderByCreatedAtDesc(
                        ownerId,
                        AiExecutionTargetType.ROADMAP,
                        roadmapId,
                        AiPurpose.ROADMAP_GENERATION,
                        ACTIVE_STATUSES)
                .orElse(null);
        if (active != null) {
            return AiExecutionResponse.from(active);
        }

        UserAccount owner = userAccountRepository.findById(ownerId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.AUTHENTICATION_REQUIRED,
                        "The authenticated account is unavailable."));
        AiProviderConfig providerConfig =
                providerSelector.requireDefault(AiPurpose.ROADMAP_GENERATION);
        AiExecution execution = executionRepository.saveAndFlush(AiExecution.queue(
                owner,
                providerConfig,
                AiPurpose.ROADMAP_GENERATION,
                operation,
                AiExecutionTargetType.ROADMAP,
                roadmapId,
                normalizedKey));

        if (adjustmentPrompt != null) {
            inputRepository.save(AiExecutionInput.create(
                    execution.getId(), adjustmentPrompt, Instant.now()));
        }

        auditLogService.logAction(
                ownerId,
                owner.getEmail(),
                AuditEventAction.AI_EXECUTION_QUEUED,
                "AiExecution",
                execution.getId().toString());
        return AiExecutionResponse.from(execution);
    }

    private AiExecutionResponse submitDailyPlan(
            UUID ownerId,
            UUID dailyPlanId,
            AiExecutionOperation operation,
            String idempotencyKey) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        if (normalizedKey != null) {
            AiExecution existing = findMatchingIdempotentExecution(
                    ownerId,
                    normalizedKey,
                    AiExecutionTargetType.DAILY_PLAN,
                    dailyPlanId,
                    AiPurpose.DAILY_PLAN_GENERATION,
                    operation);
            if (existing != null) {
                return AiExecutionResponse.from(existing);
            }
        }

        DailyPlan plan = dailyPlanRepository
                .findByIdAndUserId(dailyPlanId, ownerId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.DAILY_PLAN_NOT_FOUND,
                        "Daily plan not found."));
        if (plan.getStatus() != DailyPlanStatus.DRAFT
                && plan.getStatus() != DailyPlanStatus.READY) {
            throw new BusinessException(
                    ErrorCode.DAILY_PLAN_LOCKED,
                    "Cannot generate AI content after Daily Plan execution starts.");
        }

        AiExecution active = executionRepository
                .findFirstByOwnerIdAndTargetTypeAndTargetIdAndPurposeAndStatusInOrderByCreatedAtDesc(
                        ownerId,
                        AiExecutionTargetType.DAILY_PLAN,
                        dailyPlanId,
                        AiPurpose.DAILY_PLAN_GENERATION,
                        ACTIVE_STATUSES)
                .orElse(null);
        if (active != null) {
            return AiExecutionResponse.from(active);
        }

        UserAccount owner = userAccountRepository.findById(ownerId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.AUTHENTICATION_REQUIRED,
                        "The authenticated account is unavailable."));
        AiProviderConfig providerConfig =
                providerSelector.requireDefault(AiPurpose.DAILY_PLAN_GENERATION);
        AiExecution execution = executionRepository.saveAndFlush(AiExecution.queue(
                owner,
                providerConfig,
                AiPurpose.DAILY_PLAN_GENERATION,
                operation,
                AiExecutionTargetType.DAILY_PLAN,
                dailyPlanId,
                normalizedKey));

        auditLogService.logAction(
                ownerId,
                owner.getEmail(),
                AuditEventAction.AI_EXECUTION_QUEUED,
                "AiExecution",
                execution.getId().toString());
        return AiExecutionResponse.from(execution);
    }

    private AiExecution findMatchingIdempotentExecution(
            UUID ownerId,
            String idempotencyKey,
            AiExecutionTargetType targetType,
            UUID targetId,
            AiPurpose purpose,
            AiExecutionOperation operation) {
        AiExecution existing = executionRepository
                .findByOwnerIdAndIdempotencyKey(ownerId, idempotencyKey)
                .orElse(null);
        if (existing == null) {
            return null;
        }
        if (existing.getTargetType() != targetType
                || !existing.getTargetId().equals(targetId)
                || existing.getPurpose() != purpose
                || existing.getOperation() != operation) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "Idempotency-Key was already used for another AI operation.");
        }
        return existing;
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() > 100) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "Idempotency-Key must not exceed 100 characters.");
        }
        return normalized;
    }

    private String normalizePrompt(String adjustmentPrompt) {
        if (adjustmentPrompt == null || adjustmentPrompt.isBlank()) {
            return null;
        }
        return adjustmentPrompt.trim();
    }

    private BusinessException notFound() {
        return new BusinessException(
                ErrorCode.AI_EXECUTION_NOT_FOUND,
                "AI execution was not found.");
    }
}
