package com.codegym.aiplanning.service.guidance;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.entity.ai.AiExecutionOperation;
import com.codegym.aiplanning.entity.ai.AiProviderConfig;
import com.codegym.aiplanning.entity.daily.DailyPlanItem;
import com.codegym.aiplanning.entity.daily.DailyPlanVersion;
import com.codegym.aiplanning.repository.daily.DailyPlanItemRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanVersionRepository;
import com.codegym.aiplanning.service.guidance.model.GeneratedTaskGuidance;
import com.codegym.aiplanning.service.guidance.model.TaskGuidanceContext;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskGuidanceGenerationService {

    private final DailyPlanItemRepository dailyPlanItemRepository;
    private final DailyPlanVersionRepository dailyPlanVersionRepository;
    private final DailyPlanRepository dailyPlanRepository;
    private final TaskGuidanceContextBuilder contextBuilder;
    private final TaskGuidanceAiGenerator aiGenerator;
    private final TaskGuidancePersistenceService persistenceService;

    public TaskGuidanceGenerationService(
            DailyPlanItemRepository dailyPlanItemRepository,
            DailyPlanVersionRepository dailyPlanVersionRepository,
            DailyPlanRepository dailyPlanRepository,
            TaskGuidanceContextBuilder contextBuilder,
            TaskGuidanceAiGenerator aiGenerator,
            TaskGuidancePersistenceService persistenceService) {
        this.dailyPlanItemRepository = dailyPlanItemRepository;
        this.dailyPlanVersionRepository = dailyPlanVersionRepository;
        this.dailyPlanRepository = dailyPlanRepository;
        this.contextBuilder = contextBuilder;
        this.aiGenerator = aiGenerator;
        this.persistenceService = persistenceService;
    }

    public UUID generate(
            UUID executionId,
            UUID ownerId,
            UUID dailyPlanItemId,
            AiExecutionOperation operation,
            String adjustmentInstruction,
            AiProviderConfig providerConfig) {
        return generate(
                executionId,
                ownerId,
                dailyPlanItemId,
                operation,
                adjustmentInstruction,
                providerConfig,
                null);
    }

    public UUID generate(
            UUID executionId,
            UUID ownerId,
            UUID dailyPlanItemId,
            AiExecutionOperation operation,
            String adjustmentInstruction,
            AiProviderConfig providerConfig,
            String systemPrompt) {
        UUID existingResult = persistenceService.findExistingResult(
                        executionId,
                        ownerId,
                        dailyPlanItemId)
                .orElse(null);
        if (existingResult != null) {
            return existingResult;
        }
        TargetPath path = resolveOwnedPath(ownerId, dailyPlanItemId);
        TaskGuidanceContext context = contextBuilder.build(
                ownerId,
                path.dailyPlanId(),
                path.dailyPlanVersionId(),
                dailyPlanItemId);
        GeneratedTaskGuidance generated = aiGenerator.generate(
                context,
                adjustmentInstruction,
                providerConfig,
                systemPrompt);
        return persistenceService.persist(
                executionId,
                context,
                generated,
                operation);
    }

    @Transactional(readOnly = true)
    public TargetPath resolveOwnedPath(UUID ownerId, UUID dailyPlanItemId) {
        DailyPlanItem item = dailyPlanItemRepository
                .findOwnedById(ownerId, dailyPlanItemId)
                .orElseThrow(this::notFound);
        DailyPlanVersion version = dailyPlanVersionRepository
                .findById(item.getDailyPlanVersionId())
                .orElseThrow(this::notFound);
        dailyPlanRepository.findByIdAndUserId(version.getDailyPlanId(), ownerId)
                .orElseThrow(this::notFound);
        return new TargetPath(version.getDailyPlanId(), version.getId());
    }

    private BusinessException notFound() {
        return new BusinessException(
                ErrorCode.RESOURCE_NOT_FOUND,
                "Daily Plan item was not found.");
    }

    public record TargetPath(UUID dailyPlanId, UUID dailyPlanVersionId) {}
}
