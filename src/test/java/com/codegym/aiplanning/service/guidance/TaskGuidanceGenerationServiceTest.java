package com.codegym.aiplanning.service.guidance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.entity.ai.AiExecutionOperation;
import com.codegym.aiplanning.entity.ai.AiProviderConfig;
import com.codegym.aiplanning.repository.daily.DailyPlanItemRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanVersionRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskGuidanceGenerationServiceTest {

    @Mock
    private DailyPlanItemRepository dailyPlanItemRepository;

    @Mock
    private DailyPlanVersionRepository dailyPlanVersionRepository;

    @Mock
    private DailyPlanRepository dailyPlanRepository;

    @Mock
    private TaskGuidanceContextBuilder contextBuilder;

    @Mock
    private TaskGuidanceAiGenerator aiGenerator;

    @Mock
    private TaskGuidancePersistenceService persistenceService;

    private TaskGuidanceGenerationService service;

    @BeforeEach
    void setUp() {
        service = new TaskGuidanceGenerationService(
                dailyPlanItemRepository,
                dailyPlanVersionRepository,
                dailyPlanRepository,
                contextBuilder,
                aiGenerator,
                persistenceService);
    }

    @Test
    void recoveredExecutionDoesNotCallTheProviderAgain() {
        UUID executionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        AiProviderConfig providerConfig = org.mockito.Mockito.mock(
                AiProviderConfig.class);

        when(persistenceService.findExistingResult(
                        executionId,
                        ownerId,
                        itemId))
                .thenReturn(Optional.of(revisionId));

        UUID result = service.generate(
                executionId,
                ownerId,
                itemId,
                AiExecutionOperation.REGENERATE,
                "Use a smaller example",
                providerConfig);

        assertThat(result).isEqualTo(revisionId);
        verify(dailyPlanItemRepository, never()).findOwnedById(ownerId, itemId);
        verify(dailyPlanVersionRepository, never()).findById(versionId);
        verify(dailyPlanRepository, never()).findByIdAndUserId(planId, ownerId);
        verify(contextBuilder, never()).build(
                ownerId,
                planId,
                versionId,
                itemId);
        verify(aiGenerator, never()).generate(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }
}
