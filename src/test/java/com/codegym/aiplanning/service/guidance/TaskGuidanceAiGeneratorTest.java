package com.codegym.aiplanning.service.guidance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.entity.ai.AiProviderConfig;
import com.codegym.aiplanning.service.ai.AiClientService;
import com.codegym.aiplanning.service.guidance.model.GeneratedTaskGuidance;
import com.codegym.aiplanning.service.guidance.model.TaskGuidanceContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskGuidanceAiGeneratorTest {

    @Mock
    private AiClientService aiClientService;

    @Mock
    private TaskGuidanceSchemaValidator schemaValidator;

    @Mock
    private AiProviderConfig providerConfig;

    private TaskGuidanceAiGenerator generator;
    private TaskGuidanceContext context;

    @BeforeEach
    void setUp() {
        generator = new TaskGuidanceAiGenerator(
                aiClientService,
                schemaValidator,
                new ObjectMapper());
        context = new TaskGuidanceContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                0L,
                "vi",
                "PRACTICE",
                "NOT_STARTED",
                "Task",
                "Description",
                30,
                List.of(),
                null,
                List.of(),
                "a".repeat(64));
    }

    @Test
    void retriesInvalidOutputTwiceThenReturnsTheValidatedPlan() {
        GeneratedTaskGuidance expected = new GeneratedTaskGuidance(
                context.dailyPlanVersionId(),
                context.dailyPlanItemId(),
                "Objective",
                "Summary",
                List.of(),
                List.of());
        when(aiClientService.generateContent(
                        any(AiProviderConfig.class),
                        anyString(),
                        anyString()))
                .thenReturn("invalid-one", "invalid-two", "valid");
        when(schemaValidator.validate(anyString(), any(TaskGuidanceContext.class)))
                .thenThrow(new InvalidAiTaskGuidanceResponseException("invalid"))
                .thenThrow(new InvalidAiTaskGuidanceResponseException("invalid"))
                .thenReturn(expected);

        GeneratedTaskGuidance actual = generator.generate(
                context,
                "Focus on one example",
                providerConfig);

        assertThat(actual).isSameAs(expected);
        verify(aiClientService, times(3)).generateContent(
                any(AiProviderConfig.class),
                anyString(),
                anyString());
    }

    @Test
    void threeInvalidResponsesFailWithoutReturningPartialGuidance() {
        when(aiClientService.generateContent(
                        any(AiProviderConfig.class),
                        anyString(),
                        anyString()))
                .thenReturn("invalid");
        when(schemaValidator.validate(anyString(), any(TaskGuidanceContext.class)))
                .thenThrow(new InvalidAiTaskGuidanceResponseException("invalid"));

        assertThatThrownBy(() -> generator.generate(context, null, providerConfig))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                                ((BusinessException) exception).errorCode())
                        .isEqualTo(ErrorCode.AI_OUTPUT_INVALID));
        verify(aiClientService, times(3)).generateContent(
                any(AiProviderConfig.class),
                anyString(),
                anyString());
    }
}
