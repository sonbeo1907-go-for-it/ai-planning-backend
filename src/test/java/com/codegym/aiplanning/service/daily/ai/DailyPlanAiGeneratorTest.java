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
import com.codegym.aiplanning.entity.roadmap.RoadmapItemType;
import com.codegym.aiplanning.service.ai.AiClientService;
import com.codegym.aiplanning.service.evaluation.WeakTopicContextResolver.WeakTopicPromptContext;
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
                new DailyPlanPromptContextBuilder(),
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
                .contains("relevantTopics")
                .contains("unresolvedTasks")
                .contains("topicSignals")
                .contains(roadmapItemId.toString())
                .doesNotContain("userId")
                .doesNotContain("dailyPlanId")
                .doesNotContain("recentProgress")
                .doesNotContain("actualResult")
                .doesNotContain("\"previousPlan\":");
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

    @Test
    void generate_includesWeakTopicPromptInstructionsAndContext() {
        UUID weakTopicId = UUID.randomUUID();
        WeakTopicPromptContext weakTopic = weakLearningUnit(
                weakTopicId,
                roadmapItemId,
                "Configure authentication",
                "Spring Security",
                "Week 1",
                2,
                70.0);
        DailyPlanningContext baseContext = context(120);
        DailyPlanningContext contextWithWeakTopics = new DailyPlanningContext(
                baseContext.dailyPlanId(),
                baseContext.userId(),
                baseContext.targetDate(),
                baseContext.timeZone(),
                baseContext.availableMinutes(),
                baseContext.roadmap(),
                baseContext.recentProgress(),
                baseContext.latestTopicOutcomes(),
                baseContext.unfinishedTasks(),
                baseContext.weaknessSignals(),
                List.of(weakTopic),
                baseContext.previousPlan());

        when(aiClientService.generateContent(
                        eq(AiPurpose.DAILY_PLAN_GENERATION),
                        anyString(),
                        anyString()))
                .thenReturn(responseJson(60));

        generator.generate(contextWithWeakTopics);

        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiClientService).generateContent(
                eq(AiPurpose.DAILY_PLAN_GENERATION),
                systemPromptCaptor.capture(),
                userPromptCaptor.capture());

        assertThat(systemPromptCaptor.getValue())
                .contains("unresolvedWeakTopics")
                .contains("at most one REVIEW task")
                .contains("30%")
                .contains("REVIEW")
                .contains("placed first");

        assertThat(userPromptCaptor.getValue())
                .contains("unresolvedWeakTopics")
                .contains("Spring Security");
    }

    @Test
    void generate_whenWeakTopicsIncludeAnotherRoadmap_filtersToActiveLearningUnits() {
        WeakTopicPromptContext wt1 = weakLearningUnit(
                UUID.randomUUID(),
                roadmapItemId,
                "Configure authentication",
                "Spring Security",
                "Week 1",
                2,
                70.0);
        WeakTopicPromptContext wt2 = weakLearningUnit(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Configure audit listeners",
                "JPA Auditing",
                "Week 2",
                1,
                45.0);

        DailyPlanningContext baseContext = context(120);
        DailyPlanningContext contextWithWeakTopics = new DailyPlanningContext(
                baseContext.dailyPlanId(),
                baseContext.userId(),
                baseContext.targetDate(),
                baseContext.timeZone(),
                baseContext.availableMinutes(),
                baseContext.roadmap(),
                baseContext.recentProgress(),
                baseContext.latestTopicOutcomes(),
                baseContext.unfinishedTasks(),
                baseContext.weaknessSignals(),
                List.of(wt1, wt2),
                baseContext.previousPlan());

        when(aiClientService.generateContent(
                        eq(AiPurpose.DAILY_PLAN_GENERATION),
                        anyString(),
                        anyString()))
                .thenReturn(responseJson(60));

        generator.generate(contextWithWeakTopics);

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiClientService).generateContent(
                eq(AiPurpose.DAILY_PLAN_GENERATION),
                anyString(),
                userPromptCaptor.capture());

        assertThat(userPromptCaptor.getValue())
                .contains("Spring Security")
                .doesNotContain("JPA Auditing");
    }

    @Test
    void generate_whenNullWeakTopicsInContext_defaultsToEmptyListWithoutError() {
        DailyPlanningContext baseContext = context(60);
        DailyPlanningContext contextWithNullWeakTopics = new DailyPlanningContext(
                baseContext.dailyPlanId(),
                baseContext.userId(),
                baseContext.targetDate(),
                baseContext.timeZone(),
                baseContext.availableMinutes(),
                baseContext.roadmap(),
                baseContext.recentProgress(),
                baseContext.latestTopicOutcomes(),
                baseContext.unfinishedTasks(),
                baseContext.weaknessSignals(),
                null,
                baseContext.previousPlan());

        assertThat(contextWithNullWeakTopics.unresolvedWeakTopics()).isNotNull().isEmpty();

        when(aiClientService.generateContent(
                        eq(AiPurpose.DAILY_PLAN_GENERATION),
                        anyString(),
                        anyString()))
                .thenReturn(responseJson(60));

        DailyPlanAiGenerator.GeneratedDailyPlan plan = generator.generate(contextWithNullWeakTopics);
        assertThat(plan.response().items()).hasSize(1);
    }

    @Test
    void generate_parsesValidReviewTaskTargetingWeakTopicAtBeginningWithinBudget() {
        // availableMinutes = 120, review task = 30 mins (25%, below the 30% cap),
        // new material = 60 mins, so total planned time is 90 mins.
        String responseWithReviewFirst = """
                {
                  "summary": "Kế hoạch ngày mới ưu tiên ôn tập kiến thức yếu",
                  "items": [
                    {
                      "roadmapItemId": "%s",
                      "title": "Ôn tập Spring Security",
                      "description": "Củng cố lỗ hổng kiến thức",
                      "category": "REVIEW",
                      "plannedMinutes": 30,
                      "aiAdjustmentAction": null,
                      "aiAdjustmentReason": null
                    },
                    {
                      "roadmapItemId": "%s",
                      "title": "Học bài mới Spring Data JPA",
                      "description": "Kiến thức bài mới",
                      "category": "NEW_MATERIAL",
                      "plannedMinutes": 60,
                      "aiAdjustmentAction": null,
                      "aiAdjustmentReason": null
                    }
                  ],
                  "adjustments": []
                }
                """.formatted(roadmapItemId, roadmapItemId);

        when(aiClientService.generateContent(
                        eq(AiPurpose.DAILY_PLAN_GENERATION),
                        anyString(),
                        anyString()))
                .thenReturn(responseWithReviewFirst);

        DailyPlanAiGenerator.GeneratedDailyPlan result = generator.generate(context(120));

        assertThat(result.response().items()).hasSize(2);
        // Verify REVIEW task is first
        assertThat(result.response().items().get(0).category().name()).isEqualTo("REVIEW");
        assertThat(result.response().items().get(0).plannedMinutes()).isEqualTo(30);
        // Verify 30 minutes is 25% of 120 minutes and remains within the cap.
        double reviewPercentage = (double) result.response().items().get(0).plannedMinutes() / 120.0 * 100.0;
        assertThat(reviewPercentage).isLessThanOrEqualTo(30.0);
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
                List.of(new DailyPlanningContext.LatestTopicOutcome(
                        roadmapItemId,
                        DailyTaskStatus.PARTIALLY_COMPLETED,
                        50,
                        4,
                        2,
                        java.time.Instant.now(),
                        LocalDate.now().minusDays(1))),
                List.of(unfinished),
                List.of(new DailyPlanningContext.WeaknessSignal(
                        roadmapItemId,
                        "Spring",
                        4,
                        2,
                        DailyTaskStatus.PARTIALLY_COMPLETED,
                        "reported difficulty is high")),
                List.of(),
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

    private WeakTopicPromptContext weakLearningUnit(
            UUID weakTopicId,
            UUID learningUnitId,
            String learningUnitTitle,
            String topicTitle,
            String milestoneTitle,
            Integer rating,
            Double score) {
        return new WeakTopicPromptContext(
                weakTopicId,
                learningUnitId,
                RoadmapItemType.LEARNING_UNIT,
                learningUnitId,
                learningUnitTitle,
                UUID.randomUUID(),
                topicTitle,
                UUID.randomUUID(),
                milestoneTitle,
                rating,
                score);
    }
}
