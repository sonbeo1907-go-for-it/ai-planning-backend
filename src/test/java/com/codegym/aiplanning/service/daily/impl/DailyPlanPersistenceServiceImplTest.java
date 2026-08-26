package com.codegym.aiplanning.service.daily.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.entity.daily.DailyPlan;
import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.entity.daily.DailyPlanItem;
import com.codegym.aiplanning.entity.daily.DailyPlanVersion;
import com.codegym.aiplanning.entity.daily.DailyPlanVersionOrigin;
import com.codegym.aiplanning.entity.daily.DailyPlanVersionStatus;
import com.codegym.aiplanning.entity.daily.DailyTaskCategory;
import com.codegym.aiplanning.repository.daily.DailyPlanItemRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanVersionRepository;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.service.daily.ai.DailyPlanAiResponse;
import java.time.LocalDate;
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
class DailyPlanPersistenceServiceImplTest {

    @Mock
    private DailyPlanRepository dailyPlanRepository;
    @Mock
    private DailyPlanVersionRepository dailyPlanVersionRepository;
    @Mock
    private DailyPlanItemRepository dailyPlanItemRepository;
    @Mock
    private AuditLogService auditLogService;

    private DailyPlanPersistenceServiceImpl persistenceService;

    private UUID userId;
    private UUID planId;
    private UUID activeVersionId;
    private DailyPlan plan;
    private DailyPlanVersion activeVersion;

    @BeforeEach
    void setUp() {
        persistenceService = new DailyPlanPersistenceServiceImpl(
                dailyPlanRepository,
                dailyPlanVersionRepository,
                dailyPlanItemRepository,
                auditLogService);

        userId = UUID.randomUUID();
        planId = UUID.randomUUID();
        activeVersionId = UUID.randomUUID();

        plan = DailyPlan.create(userId, LocalDate.now(), "UTC");
        ReflectionTestUtils.setField(plan, "id", planId);
        plan.updateActiveVersion(activeVersionId);

        activeVersion = DailyPlanVersion.create(planId, 1, DailyPlanVersionOrigin.MANUAL, 60, 0);
        ReflectionTestUtils.setField(activeVersion, "id", activeVersionId);
    }

    @Test
    void persistAiGeneratedDraft_withOldDraft_supersedesOldDraft() {
        UUID oldDraftId = UUID.randomUUID();
        DailyPlanVersion oldDraft = DailyPlanVersion.create(planId, 2, DailyPlanVersionOrigin.USER_EDITED, 60, 0);
        ReflectionTestUtils.setField(oldDraft, "id", oldDraftId);

        when(dailyPlanRepository.findByIdAndUserIdForUpdate(planId, userId)).thenReturn(Optional.of(plan));
        when(dailyPlanVersionRepository.findByDailyPlanIdAndStatus(planId, DailyPlanVersionStatus.DRAFT))
                .thenReturn(Optional.of(oldDraft));
        when(dailyPlanVersionRepository.findTopByDailyPlanIdOrderByVersionNumberDesc(planId))
                .thenReturn(Optional.of(oldDraft));

        when(dailyPlanVersionRepository.saveAndFlush(any(DailyPlanVersion.class))).thenAnswer(inv -> {
            DailyPlanVersion version = inv.getArgument(0);
            ReflectionTestUtils.setField(version, "id", UUID.randomUUID());
            return version;
        });

        List<DailyPlanAiResponse.AiPlanItemDto> aiItems = List.of(
                new DailyPlanAiResponse.AiPlanItemDto(UUID.randomUUID(), "T1", null, DailyTaskCategory.CUSTOM, 20, null, null)
        );

        DailyPlanVersion result = persistenceService.persistAiGeneratedDraft(
                planId,
                userId,
                "test",
                20,
                "AI Explain",
                false,
                "request-1",
                aiItems);

        // Assert old draft is superseded
        assertThat(oldDraft.getStatus()).isEqualTo(DailyPlanVersionStatus.SUPERSEDED);

        // Assert new draft is AI_REGENERATED
        assertThat(result.getStatus()).isEqualTo(DailyPlanVersionStatus.DRAFT);
        assertThat(result.getOrigin()).isEqualTo(DailyPlanVersionOrigin.AI_REGENERATED);
        assertThat(result.getVersionNumber()).isEqualTo(3);
        assertThat(result.getAiExplanation()).isEqualTo("AI Explain");
        assertThat(result.getGenerationRequestKey()).isEqualTo("request-1");
    }

    @Test
    void persistAiGeneratedDraft_withoutOldDraft_createsNewDraft() {
        when(dailyPlanRepository.findByIdAndUserIdForUpdate(planId, userId)).thenReturn(Optional.of(plan));
        when(dailyPlanVersionRepository.findByDailyPlanIdAndStatus(planId, DailyPlanVersionStatus.DRAFT))
                .thenReturn(Optional.empty());
        when(dailyPlanVersionRepository.findTopByDailyPlanIdOrderByVersionNumberDesc(planId))
                .thenReturn(Optional.of(activeVersion));

        when(dailyPlanVersionRepository.saveAndFlush(any(DailyPlanVersion.class))).thenAnswer(inv -> {
            DailyPlanVersion version = inv.getArgument(0);
            ReflectionTestUtils.setField(version, "id", UUID.randomUUID());
            return version;
        });

        List<DailyPlanAiResponse.AiPlanItemDto> aiItems = List.of(
                new DailyPlanAiResponse.AiPlanItemDto(UUID.randomUUID(), "T1", null, DailyTaskCategory.CUSTOM, 20, null, null)
        );

        DailyPlanVersion result = persistenceService.persistAiGeneratedDraft(
                planId,
                userId,
                "test",
                20,
                "AI Explain",
                false,
                null,
                aiItems);

        assertThat(result.getStatus()).isEqualTo(DailyPlanVersionStatus.DRAFT);
        assertThat(result.getOrigin()).isEqualTo(DailyPlanVersionOrigin.AI_GENERATED);
        assertThat(result.getVersionNumber()).isEqualTo(2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DailyPlanItem>> itemCaptor = ArgumentCaptor.forClass(List.class);
        verify(dailyPlanItemRepository).saveAll(itemCaptor.capture());
        DailyPlanItem savedItem = itemCaptor.getValue().get(0);
        assertThat(savedItem.getTitle()).isEqualTo("T1");
        assertThat(savedItem.getPlannedMinutes()).isEqualTo(20);
    }

    @Test
    void persistAiGeneratedDraft_rejectsOverBudgetBeforeSupersedingExistingDraft() {
        when(dailyPlanRepository.findByIdAndUserIdForUpdate(planId, userId))
                .thenReturn(Optional.of(plan));
        when(dailyPlanVersionRepository.findTopByDailyPlanIdOrderByVersionNumberDesc(planId))
                .thenReturn(Optional.of(activeVersion));

        assertThatThrownBy(() -> persistenceService.persistAiGeneratedDraft(
                        planId,
                        userId,
                        "test",
                        61,
                        "AI Explain",
                        false,
                        "request-over-budget",
                        List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("available-time budget");
    }
}
