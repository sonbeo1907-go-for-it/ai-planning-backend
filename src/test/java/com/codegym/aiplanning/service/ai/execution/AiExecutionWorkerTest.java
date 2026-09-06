package com.codegym.aiplanning.service.ai.execution;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.daily.dto.DailyPlanVersionResponse;
import com.codegym.aiplanning.controller.evaluation.dto.QuizDetailResponse;
import com.codegym.aiplanning.controller.roadmap.dto.RoadmapVersionResponse;
import com.codegym.aiplanning.entity.ai.AiExecution;
import com.codegym.aiplanning.entity.ai.AiExecutionOperation;
import com.codegym.aiplanning.entity.ai.AiExecutionResultType;
import com.codegym.aiplanning.entity.ai.AiExecutionTargetType;
import com.codegym.aiplanning.entity.ai.AiProviderConfig;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.repository.ai.AiExecutionInputRepository;
import com.codegym.aiplanning.repository.ai.AiExecutionRepository;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.service.daily.DailyPlanService;
import com.codegym.aiplanning.service.roadmap.AiRoadmapGeneratorService;
import com.codegym.aiplanning.service.evaluation.DailyEvaluationService;
import com.codegym.aiplanning.service.evaluation.WeakTopicService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class AiExecutionWorkerTest {

    @Mock
    private AiExecutionRepository executionRepository;

    @Mock
    private AiExecutionInputRepository inputRepository;

    @Mock
    private AiRoadmapGeneratorService roadmapGeneratorService;

    @Mock
    private DailyPlanService dailyPlanService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private DailyEvaluationService dailyEvaluationService;

    @Mock
    private WeakTopicService weakTopicService;

    private AiExecutionWorker worker;
    private UUID executionId;
    private UUID ownerId;
    private UUID roadmapId;
    private AiExecution execution;
    private AiProviderConfig providerConfig;

    @BeforeEach
    void setUp() {
        worker = new AiExecutionWorker(
                executionRepository,
                inputRepository,
                roadmapGeneratorService,
                dailyPlanService,
                auditLogService,
                transactionTemplate,
                dailyEvaluationService,
                weakTopicService);
        executionId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        roadmapId = UUID.randomUUID();
        execution = mock(AiExecution.class);
        providerConfig = mock(AiProviderConfig.class);

        UserAccount owner = mock(UserAccount.class);
        when(owner.getId()).thenReturn(ownerId);
        when(owner.getEmail()).thenReturn("user@example.com");
        when(execution.getId()).thenReturn(executionId);
        when(execution.getOwner()).thenReturn(owner);
        when(execution.getTargetId()).thenReturn(roadmapId);
        when(execution.getTargetType()).thenReturn(AiExecutionTargetType.ROADMAP);
        when(execution.getOperation()).thenReturn(AiExecutionOperation.GENERATE);
        when(execution.getProviderConfig()).thenReturn(providerConfig);

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        org.mockito.Mockito.doAnswer(invocation -> {
                    java.util.function.Consumer<TransactionStatus> callback =
                            invocation.getArgument(0);
                    callback.accept(mock(TransactionStatus.class));
                    return null;
                })
                .when(transactionTemplate)
                .executeWithoutResult(any());
    }

    @Test
    void claimedExecutionRunsOutsideTheRequestAndPublishesItsResult() {
        UUID versionId = UUID.randomUUID();
        RoadmapVersionResponse version = mock(RoadmapVersionResponse.class);
        when(version.id()).thenReturn(versionId);
        when(executionRepository.claimQueued(eq(executionId), any(Instant.class), any(Instant.class)))
                .thenReturn(1);
        when(executionRepository.findJobContextById(executionId))
                .thenReturn(Optional.of(execution));
        when(inputRepository.findById(executionId)).thenReturn(Optional.empty());
        when(roadmapGeneratorService.generateWithProviderConfig(
                        ownerId, roadmapId, providerConfig))
                .thenReturn(version);
        when(executionRepository.findByIdForUpdate(executionId))
                .thenReturn(Optional.of(execution));
        when(execution.isRunning()).thenReturn(true);

        worker.executeAsync(executionId);

        verify(roadmapGeneratorService).generateWithProviderConfig(
                ownerId, roadmapId, providerConfig);
        verify(execution).markSucceeded(
                eq(AiExecutionResultType.ROADMAP_VERSION),
                eq(versionId),
                any(Instant.class));
        verify(inputRepository).deleteByExecutionId(executionId);
        verify(auditLogService).logAction(
                ownerId,
                "user@example.com",
                AuditEventAction.AI_EXECUTION_SUCCEEDED,
                "AiExecution",
                executionId.toString());
    }

    @Test
    void providerFailureBecomesAQueryableFailedExecution() {
        when(executionRepository.claimQueued(eq(executionId), any(Instant.class), any(Instant.class)))
                .thenReturn(1);
        when(executionRepository.findJobContextById(executionId))
                .thenReturn(Optional.of(execution));
        when(inputRepository.findById(executionId)).thenReturn(Optional.empty());
        when(roadmapGeneratorService.generateWithProviderConfig(
                        ownerId, roadmapId, providerConfig))
                .thenThrow(new BusinessException(
                        ErrorCode.AI_PROVIDER_UNAVAILABLE,
                        "Provider unavailable"));
        when(executionRepository.findByIdForUpdate(executionId))
                .thenReturn(Optional.of(execution));
        when(execution.isRunning()).thenReturn(true);

        worker.executeAsync(executionId);

        verify(execution).markFailed(
                eq(ErrorCode.AI_PROVIDER_UNAVAILABLE.name()),
                eq("Provider unavailable"),
                any(Instant.class));
        verify(auditLogService).logAction(
                ownerId,
                "user@example.com",
                AuditEventAction.AI_EXECUTION_FAILED,
                "AiExecution",
                executionId.toString());
        verify(execution, never()).markSucceeded(any(), any(), any());
    }

    @Test
    void dailyPlanExecutionPublishesAnExactDailyPlanVersionResult() {
        UUID versionId = UUID.randomUUID();
        DailyPlanVersionResponse version = mock(DailyPlanVersionResponse.class);
        when(version.id()).thenReturn(versionId);
        when(execution.getTargetType()).thenReturn(AiExecutionTargetType.DAILY_PLAN);
        when(executionRepository.claimQueued(eq(executionId), any(Instant.class), any(Instant.class)))
                .thenReturn(1);
        when(executionRepository.findJobContextById(executionId))
                .thenReturn(Optional.of(execution));
        when(inputRepository.findById(executionId)).thenReturn(Optional.empty());
        when(dailyPlanService.generateAiDraftVersionWithProviderConfig(
                        roadmapId,
                        ownerId,
                        "user@example.com",
                        executionId.toString(),
                        providerConfig))
                .thenReturn(version);
        when(executionRepository.findByIdForUpdate(executionId))
                .thenReturn(Optional.of(execution));
        when(execution.isRunning()).thenReturn(true);

        worker.executeAsync(executionId);

        verify(dailyPlanService).generateAiDraftVersionWithProviderConfig(
                roadmapId,
                ownerId,
                "user@example.com",
                executionId.toString(),
                providerConfig);
        verify(execution).markSucceeded(
                eq(AiExecutionResultType.DAILY_PLAN_VERSION),
                eq(versionId),
                any(Instant.class));
        verify(roadmapGeneratorService, never())
                .generateWithProviderConfig(any(), any(), any());
    }

    @Test
    void dailyPlanVersionExecutionPublishesAnExactQuizResult() {
        UUID quizId = UUID.randomUUID();
        QuizDetailResponse quiz = mock(QuizDetailResponse.class);
        when(quiz.id()).thenReturn(quizId);
        when(execution.getTargetType()).thenReturn(AiExecutionTargetType.DAILY_PLAN_VERSION);
        prepareClaimedExecution();
        when(dailyEvaluationService.generateDailyQuizWithProviderConfig(
                        ownerId,
                        roadmapId,
                        providerConfig))
                .thenReturn(quiz);

        worker.executeAsync(executionId);

        verify(dailyEvaluationService).generateDailyQuizWithProviderConfig(
                ownerId,
                roadmapId,
                providerConfig);
        verify(execution).markSucceeded(
                eq(AiExecutionResultType.QUIZ),
                eq(quizId),
                any(Instant.class));
        verify(roadmapGeneratorService, never())
                .generateWithProviderConfig(any(), any(), any());
    }

    @Test
    void weakTopicExecutionPublishesAnExactMasteryQuizResult() {
        UUID quizId = UUID.randomUUID();
        QuizDetailResponse quiz = mock(QuizDetailResponse.class);
        when(quiz.id()).thenReturn(quizId);
        when(execution.getTargetType()).thenReturn(AiExecutionTargetType.WEAK_TOPIC);
        prepareClaimedExecution();
        when(weakTopicService.generateMasteryCheckQuizWithProviderConfig(
                        ownerId,
                        roadmapId,
                        providerConfig))
                .thenReturn(quiz);

        worker.executeAsync(executionId);

        verify(weakTopicService).generateMasteryCheckQuizWithProviderConfig(
                ownerId,
                roadmapId,
                providerConfig);
        verify(execution).markSucceeded(
                eq(AiExecutionResultType.QUIZ),
                eq(quizId),
                any(Instant.class));
        verify(dailyEvaluationService, never())
                .generateDailyQuizWithProviderConfig(any(), any(), any());
    }

    private void prepareClaimedExecution() {
        when(executionRepository.claimQueued(
                        eq(executionId),
                        any(Instant.class),
                        any(Instant.class)))
                .thenReturn(1);
        when(executionRepository.findJobContextById(executionId))
                .thenReturn(Optional.of(execution));
        when(inputRepository.findById(executionId)).thenReturn(Optional.empty());
        when(executionRepository.findByIdForUpdate(executionId))
                .thenReturn(Optional.of(execution));
        when(execution.isRunning()).thenReturn(true);
    }
}
