package com.codegym.aiplanning.service.ai.execution;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.roadmap.dto.RoadmapVersionResponse;
import com.codegym.aiplanning.entity.ai.AiExecution;
import com.codegym.aiplanning.entity.ai.AiExecutionOperation;
import com.codegym.aiplanning.entity.ai.AiExecutionResultType;
import com.codegym.aiplanning.entity.ai.AiExecutionTargetType;
import com.codegym.aiplanning.entity.ai.AiProviderConfig;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.repository.ai.AiExecutionInputRepository;
import com.codegym.aiplanning.repository.ai.AiExecutionRepository;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.service.roadmap.AiRoadmapGeneratorService;
import com.codegym.aiplanning.service.daily.DailyPlanService;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class AiExecutionWorker {

    private static final Logger log = LoggerFactory.getLogger(AiExecutionWorker.class);
    private static final Duration EXECUTION_LEASE = Duration.ofMinutes(10);

    private final AiExecutionRepository executionRepository;
    private final AiExecutionInputRepository inputRepository;
    private final AiRoadmapGeneratorService roadmapGeneratorService;
    private final DailyPlanService dailyPlanService;
    private final AuditLogService auditLogService;
    private final TransactionTemplate transactionTemplate;

    public AiExecutionWorker(
            AiExecutionRepository executionRepository,
            AiExecutionInputRepository inputRepository,
            AiRoadmapGeneratorService roadmapGeneratorService,
            DailyPlanService dailyPlanService,
            AuditLogService auditLogService,
            TransactionTemplate transactionTemplate) {
        this.executionRepository = executionRepository;
        this.inputRepository = inputRepository;
        this.roadmapGeneratorService = roadmapGeneratorService;
        this.dailyPlanService = dailyPlanService;
        this.auditLogService = auditLogService;
        this.transactionTemplate = transactionTemplate;
    }

    @Async("aiGenerationExecutor")
    public void executeAsync(UUID executionId) {
        JobContext context = claim(executionId);
        if (context == null) {
            return;
        }

        try {
            GenerationResult result = execute(context);
            completeSuccessfully(context, result.resultType(), result.resultId());
        } catch (BusinessException exception) {
            completeWithFailure(
                    context,
                    exception.errorCode().name(),
                    sanitizedMessage(exception));
        } catch (RuntimeException exception) {
            completeWithFailure(
                    context,
                    ErrorCode.AI_GENERATION_FAILED.name(),
                    "AI execution failed unexpectedly.");
        }
    }

    private GenerationResult execute(JobContext context) {
        if (context.targetType() == AiExecutionTargetType.DAILY_PLAN) {
            UUID versionId = dailyPlanService.generateAiDraftVersionWithProviderConfig(
                    context.targetId(),
                    context.ownerId(),
                    context.ownerEmail(),
                    context.executionId().toString(),
                    context.providerConfig()).id();
            return new GenerationResult(
                    AiExecutionResultType.DAILY_PLAN_VERSION, versionId);
        }

        RoadmapVersionResponse version = context.operation() == AiExecutionOperation.GENERATE
                ? roadmapGeneratorService.generateWithProviderConfig(
                        context.ownerId(), context.targetId(), context.providerConfig())
                : roadmapGeneratorService.regenerateWithProviderConfig(
                        context.ownerId(),
                        context.targetId(),
                        context.adjustmentPrompt(),
                        context.providerConfig());
        return new GenerationResult(
                AiExecutionResultType.ROADMAP_VERSION, version.id());
    }

    private JobContext claim(UUID executionId) {
        return transactionTemplate.execute(status -> {
            Instant now = Instant.now();
            int claimed = executionRepository.claimQueued(
                    executionId, now, now.plus(EXECUTION_LEASE));
            if (claimed != 1) {
                return null;
            }

            AiExecution execution = executionRepository.findJobContextById(executionId)
                    .orElse(null);
            if (execution == null) {
                return null;
            }
            String prompt = inputRepository.findById(executionId)
                    .map(input -> input.getAdjustmentPrompt())
                    .orElse(null);
            return new JobContext(
                    execution.getId(),
                    execution.getOwner().getId(),
                    execution.getOwner().getEmail(),
                    execution.getTargetId(),
                    execution.getTargetType(),
                    execution.getOperation(),
                    execution.getProviderConfig(),
                    prompt);
        });
    }

    private void completeSuccessfully(
            JobContext context,
            AiExecutionResultType resultType,
            UUID resultId) {
        transactionTemplate.executeWithoutResult(status -> {
            AiExecution execution = executionRepository
                    .findByIdForUpdate(context.executionId())
                    .orElse(null);
            if (execution == null || !execution.isRunning()) {
                return;
            }
            execution.markSucceeded(resultType, resultId, Instant.now());
            executionRepository.save(execution);
            inputRepository.deleteByExecutionId(context.executionId());
            auditLogService.logAction(
                    context.ownerId(),
                    context.ownerEmail(),
                    AuditEventAction.AI_EXECUTION_SUCCEEDED,
                    "AiExecution",
                    context.executionId().toString());
        });
    }

    private void completeWithFailure(
            JobContext context, String failureCode, String failureMessage) {
        log.warn(
                "AI execution {} failed with code {}.",
                context.executionId(),
                failureCode);
        transactionTemplate.executeWithoutResult(status -> {
            AiExecution execution = executionRepository
                    .findByIdForUpdate(context.executionId())
                    .orElse(null);
            if (execution == null || !execution.isRunning()) {
                return;
            }
            execution.markFailed(failureCode, failureMessage, Instant.now());
            executionRepository.save(execution);
            inputRepository.deleteByExecutionId(context.executionId());
            auditLogService.logAction(
                    context.ownerId(),
                    context.ownerEmail(),
                    AuditEventAction.AI_EXECUTION_FAILED,
                    "AiExecution",
                    context.executionId().toString());
        });
    }

    private String sanitizedMessage(BusinessException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "AI execution failed.";
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private record JobContext(
            UUID executionId,
            UUID ownerId,
            String ownerEmail,
            UUID targetId,
            AiExecutionTargetType targetType,
            AiExecutionOperation operation,
            AiProviderConfig providerConfig,
            String adjustmentPrompt) {}

    private record GenerationResult(
            AiExecutionResultType resultType, UUID resultId) {}
}
