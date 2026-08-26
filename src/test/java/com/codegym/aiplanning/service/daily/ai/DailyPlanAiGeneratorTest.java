package com.codegym.aiplanning.service.daily.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.entity.ai.AiPurpose;
import com.codegym.aiplanning.entity.daily.DailyTaskStatus;
import com.codegym.aiplanning.service.ai.AiClientService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DailyPlanAiGeneratorTest {

    @Mock
    private AiClientService aiClientService;

    private DailyPlanAiGenerator generator;
    private UUID roadmapItemId;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        generator = new DailyPlanAiGenerator(
                aiClientService,
                new AiPlanParser(objectMapper),
                new DailyPlanValidator(),
                new DailyPlanConstraintEvaluator(),
                objectMapper);
        roadmapItemId = UUID.randomUUID();
    }

    @Test
    void generate_retriesInvalidOverBudgetOutputAndUsesDailyPlanPurpose() {
        DailyPlanningContext context = context(60);
        when(aiClientService.generateContent(
                        eq(AiPurpose.DAILY_PLAN_GENERATION),
                        anyString(),
                        anyString()))
                .thenReturn(responseJson(90))
                .thenReturn(responseJson(60));

        DailyPlanAiGenerator.GeneratedDailyPlan result = generator.generate(context);

        assertThat(result.response().items()).hasSize(1);
        assertThat(result.response().items().get(0).plannedMinutes()).isEqualTo(60);
        verify(aiClientService, times(2)).generateContent(
                eq(AiPurpose.DAILY_PLAN_GENERATION),
                anyString(),
                anyString());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiClientService, times(2)).generateContent(
                eq(AiPurpose.DAILY_PLAN_GENERATION),
                anyString(),
                promptCaptor.capture());
        assertThat(promptCaptor.getAllValues().get(0))
                .contains("unfinishedTasks")
                .contains("weaknessSignals")
                .contains(roadmapItemId.toString());
    }

    @Test
    void generate_rejectsAfterThreeInvalidResponses() {
        when(aiClientService.generateContent(
                        eq(AiPurpose.DAILY_PLAN_GENERATION),
                        anyString(),
                        anyString()))
                .thenReturn("{\"items\":[]}");

        assertThatThrownBy(() -> generator.generate(context(60)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.AI_OUTPUT_INVALID);

        verify(aiClientService, times(3)).generateContent(
                eq(AiPurpose.DAILY_PLAN_GENERATION),
                anyString(),
                anyString());
    }

    private DailyPlanningContext context(int availableMinutes) {
        UUID priorItemId = UUID.randomUUID();
        DailyPlanningContext.UnfinishedTask unfinished =
                new DailyPlanningContext.UnfinishedTask(
                        priorItemId,
                        roadmapItemId,
                        "Prior task",
                        null,
                        DailyTaskStatus.PARTIALLY_COMPLETED,
                        60);
        return new DailyPlanningContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.now(),
                "Asia/Ho_Chi_Minh",
                availableMinutes,
                new DailyPlanningContext.RoadmapContext(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        2,
                        "Java Backend",
                        null,
                        List.of(new DailyPlanningContext.RoadmapTopic(
                                roadmapItemId,
                                UUID.randomUUID(),
                                "Week 1",
                                "Spring",
                                null,
                                120,
                                0))),
                List.of(),
                List.of(unfinished),
                List.of(new DailyPlanningContext.WeaknessSignal(
                        roadmapItemId,
                        "Spring",
                        4,
                        2,
                        DailyTaskStatus.PARTIALLY_COMPLETED,
                        "reported difficulty is high")),
                new DailyPlanningContext.PreviousPlan(
                        UUID.randomUUID(),
                        LocalDate.now().minusDays(1),
                        UUID.randomUUID(),
                        List.of(unfinished)));
    }

    private String responseJson(int plannedMinutes) {
        return """
                {
                  "summary": "Kế hoạch cân bằng",
                  "items": [
                    {
                      "roadmapItemId": "%s",
                      "title": "Luyện tập Spring",
                      "description": null,
                      "category": "PRACTICE",
                      "plannedMinutes": %d,
                      "aiAdjustmentAction": null,
                      "aiAdjustmentReason": null
                    }
                  ],
                  "adjustments": []
                }
                """.formatted(roadmapItemId, plannedMinutes);
    }
}
