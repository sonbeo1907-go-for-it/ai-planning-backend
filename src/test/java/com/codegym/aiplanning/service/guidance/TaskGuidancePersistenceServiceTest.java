package com.codegym.aiplanning.service.guidance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.entity.ai.AiExecutionOperation;
import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.entity.guidance.GuidanceReferenceProvenance;
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
import com.codegym.aiplanning.service.guidance.model.TaskGuidanceContext.TaskStepSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TaskGuidancePersistenceServiceTest {

    @Mock
    private TaskGuidanceRepository taskGuidanceRepository;

    @Mock
    private TaskGuidanceRevisionRepository revisionRepository;

    @Mock
    private TaskStepGuidanceRepository stepGuidanceRepository;

    @Mock
    private TaskGuidanceReferenceRepository referenceRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    private TaskGuidancePersistenceService service;
    private UUID ownerId;
    private UUID executionId;
    private UUID stepId;
    private UUID materialId;
    private UserAccount owner;
    private TaskGuidanceContext context;
    private GeneratedTaskGuidance generated;

    @BeforeEach
    void setUp() {
        service = new TaskGuidancePersistenceService(
                taskGuidanceRepository,
                revisionRepository,
                stepGuidanceRepository,
                referenceRepository,
                userAccountRepository);
        ownerId = UUID.randomUUID();
        executionId = UUID.randomUUID();
        stepId = UUID.randomUUID();
        materialId = UUID.randomUUID();
        owner = UserAccount.create(
                "learner@example.com",
                "password-hash",
                UserRole.USER,
                AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(owner, "id", ownerId);
        context = context();
        generated = generated();

    }

    @Test
    void initialGenerationCreatesRevisionOneWithExactStepSnapshotAndReferences() {
        stubPersistenceSaves();
        when(taskGuidanceRepository.findOwnedByItemIdForUpdate(
                        context.dailyPlanItemId(), ownerId))
                .thenReturn(Optional.empty());
        when(userAccountRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(taskGuidanceRepository.saveAndFlush(any(TaskGuidance.class)))
                .thenAnswer(invocation -> withGeneratedId(invocation.getArgument(0)));
        when(revisionRepository.findFirstByTaskGuidanceIdOrderByRevisionNumberDesc(any()))
                .thenReturn(Optional.empty());
        when(revisionRepository
                        .findFirstByTaskGuidanceIdAndStatusOrderByRevisionNumberDesc(
                                any(),
                                any()))
                .thenReturn(Optional.empty());

        UUID revisionId = service.persist(
                executionId,
                context,
                generated,
                AiExecutionOperation.GENERATE);

        ArgumentCaptor<TaskGuidanceRevision> revisionCaptor =
                ArgumentCaptor.forClass(TaskGuidanceRevision.class);
        verify(revisionRepository).saveAndFlush(revisionCaptor.capture());
        TaskGuidanceRevision revision = revisionCaptor.getValue();
        assertThat(revisionId).isEqualTo(revision.getId());
        assertThat(revision.getRevisionNumber()).isEqualTo(1);
        assertThat(revision.getStatus()).isEqualTo(TaskGuidanceRevisionStatus.DRAFT);
        assertThat(revision.getAiExecutionId()).isEqualTo(executionId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TaskStepGuidance>> stepCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(stepGuidanceRepository).saveAllAndFlush(stepCaptor.capture());
        assertThat(stepCaptor.getValue()).singleElement().satisfies(step -> {
            assertThat(step.getSourceTaskStepId()).isEqualTo(stepId);
            assertThat(step.getTaskStepEntityVersion()).isEqualTo(4L);
            assertThat(step.getOrderIndex()).isZero();
        });

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TaskGuidanceReference>> referenceCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(referenceRepository, org.mockito.Mockito.times(2))
                .saveAll(referenceCaptor.capture());
        List<TaskGuidanceReference> savedReferences = referenceCaptor
                .getAllValues()
                .stream()
                .flatMap(List::stream)
                .toList();
        assertThat(savedReferences)
                .extracting(TaskGuidanceReference::getProvenance)
                .containsExactlyInAnyOrder(
                        GuidanceReferenceProvenance.MATERIAL,
                        GuidanceReferenceProvenance.UNVERIFIED_EXTERNAL);
    }

    @Test
    void regenerationSupersedesTheCurrentDraftAndPreservesItAsHistory() {
        stubPersistenceSaves();
        TaskGuidance root = root();
        TaskGuidanceRevision previous = revision(root, 1);
        when(taskGuidanceRepository.findOwnedByItemIdForUpdate(
                        context.dailyPlanItemId(), ownerId))
                .thenReturn(Optional.of(root));
        when(revisionRepository.findFirstByTaskGuidanceIdOrderByRevisionNumberDesc(
                        root.getId()))
                .thenReturn(Optional.of(previous));
        when(revisionRepository
                        .findFirstByTaskGuidanceIdAndStatusOrderByRevisionNumberDesc(
                                root.getId(),
                                TaskGuidanceRevisionStatus.DRAFT))
                .thenReturn(Optional.of(previous));

        service.persist(
                executionId,
                context,
                generated,
                AiExecutionOperation.REGENERATE);

        assertThat(previous.getStatus())
                .isEqualTo(TaskGuidanceRevisionStatus.SUPERSEDED);
        ArgumentCaptor<TaskGuidanceRevision> revisionCaptor =
                ArgumentCaptor.forClass(TaskGuidanceRevision.class);
        verify(revisionRepository, org.mockito.Mockito.times(2))
                .saveAndFlush(revisionCaptor.capture());
        TaskGuidanceRevision next = revisionCaptor.getAllValues().get(1);
        assertThat(next.getRevisionNumber()).isEqualTo(2);
        assertThat(next.getStatus()).isEqualTo(TaskGuidanceRevisionStatus.DRAFT);
        verify(revisionRepository, never()).delete(any());
    }

    @Test
    void initialGenerationCannotMutateAnExistingRevision() {
        TaskGuidance root = root();
        TaskGuidanceRevision previous = revision(root, 1);
        when(taskGuidanceRepository.findOwnedByItemIdForUpdate(
                        context.dailyPlanItemId(), ownerId))
                .thenReturn(Optional.of(root));
        when(revisionRepository.findFirstByTaskGuidanceIdOrderByRevisionNumberDesc(
                        root.getId()))
                .thenReturn(Optional.of(previous));

        assertThatThrownBy(() -> service.persist(
                        executionId,
                        context,
                        generated,
                        AiExecutionOperation.GENERATE))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);

        assertThat(previous.getStatus()).isEqualTo(TaskGuidanceRevisionStatus.DRAFT);
        verify(revisionRepository, never()).saveAndFlush(any());
        verify(stepGuidanceRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void retryingTheSameExecutionReturnsItsPersistedRevisionWithoutNewWrites() {
        TaskGuidance root = root();
        TaskGuidanceRevision persisted = revision(root, 2);
        ReflectionTestUtils.setField(persisted, "aiExecutionId", executionId);
        when(revisionRepository.findByAiExecutionId(executionId))
                .thenReturn(Optional.of(persisted));

        UUID result = service.persist(
                executionId,
                context,
                generated,
                AiExecutionOperation.REGENERATE);

        assertThat(result).isEqualTo(persisted.getId());
        verify(taskGuidanceRepository, never())
                .findOwnedByItemIdForUpdate(any(), any());
        verify(revisionRepository, never()).saveAndFlush(any());
        verify(stepGuidanceRepository, never()).saveAllAndFlush(any());
        verify(referenceRepository, never()).saveAll(any());
    }

    private TaskGuidanceContext context() {
        return new TaskGuidanceContext(
                ownerId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                2L,
                "vi",
                "PRACTICE",
                "NOT_STARTED",
                "Practice Optional safely",
                "Create one focused example",
                30,
                List.of(new TaskStepSnapshot(
                        stepId,
                        4L,
                        0,
                        "Build an Optional example",
                        "Use an empty value",
                        20,
                        true)),
                null,
                List.of(),
                "a".repeat(64));
    }

    private void stubPersistenceSaves() {
        when(revisionRepository.saveAndFlush(any(TaskGuidanceRevision.class)))
                .thenAnswer(invocation -> withGeneratedId(invocation.getArgument(0)));
        when(stepGuidanceRepository.saveAllAndFlush(any()))
                .thenAnswer(invocation -> {
                    List<TaskStepGuidance> values = invocation.getArgument(0);
                    values.forEach(this::withGeneratedId);
                    return values;
                });
    }

    private GeneratedTaskGuidance generated() {
        GeneratedReference materialReference = new GeneratedReference(
                GuidanceReferenceProvenance.MATERIAL,
                "Course notes",
                "Section 2",
                materialId,
                null);
        GeneratedReference externalReference = new GeneratedReference(
                GuidanceReferenceProvenance.UNVERIFIED_EXTERNAL,
                "External documentation",
                null,
                null,
                "https://docs.example.com/optional");
        GeneratedStepGuidance stepGuidance = new GeneratedStepGuidance(
                stepId,
                "Construct empty and present values, then compare the output.",
                "Both branches produce the expected fallback values.",
                "Keep the example small.",
                null,
                null,
                List.of(externalReference));
        return new GeneratedTaskGuidance(
                context.dailyPlanVersionId(),
                context.dailyPlanItemId(),
                "Understand Optional fallback behavior",
                "Run the persisted step and inspect both outcomes.",
                List.of(stepGuidance),
                List.of(materialReference));
    }

    private TaskGuidance root() {
        TaskGuidance root = TaskGuidance.create(
                owner,
                context.dailyPlanId(),
                context.dailyPlanVersionId(),
                context.dailyPlanItemId());
        return withGeneratedId(root);
    }

    private TaskGuidanceRevision revision(TaskGuidance root, int number) {
        TaskGuidanceRevision revision = TaskGuidanceRevision.draft(
                root,
                UUID.randomUUID(),
                number,
                "Earlier objective",
                "Earlier summary",
                context.dailyPlanItemEntityVersion(),
                context.fingerprint(),
                Instant.parse("2026-09-15T08:00:00Z"));
        return withGeneratedId(revision);
    }

    private <T> T withGeneratedId(T entity) {
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        return entity;
    }
}
