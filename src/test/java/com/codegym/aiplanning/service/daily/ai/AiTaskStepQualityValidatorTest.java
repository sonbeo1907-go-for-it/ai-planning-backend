package com.codegym.aiplanning.service.daily.ai;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AiTaskStepQualityValidatorTest {

    private final AiTaskStepQualityValidator validator =
            new AiTaskStepQualityValidator();

    @Test
    void acceptsConcreteOptionalTestingStepDespiteSharedCurriculumWords() {
        DailyPlanAiResponse.AiTaskStepDto step = step(
                "Write Optional tests to avoid null values",
                "Write three unit tests for empty Optional, a present value, and a fallback value.",
                AiTaskStepAction.WRITE,
                "Optional");

        assertThatCode(() -> validator.validate(
                        step,
                        "Practise safe value handling",
                        "Use Optional to avoid null values",
                        "Use Optional for safe value handling.",
                        "Modern Java"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAnchorPresentOnlyInParentTopic() {
        DailyPlanAiResponse.AiTaskStepDto step = step(
                "Implement Spring filter", "Run Spring filter tests.",
                AiTaskStepAction.IMPLEMENT, "Spring");

        assertThatThrownBy(() -> validator.validate(
                        step,
                        "Practice authentication",
                        "Implement JWT authentication",
                        "Create and check JWT signatures.",
                        "Spring Security"))
                .isInstanceOf(InvalidAiDailyPlanResponseException.class)
                .hasMessageContaining("outside its supplied Learning Unit context");
    }

    @Test
    void rejectsExactCopiedUnitTitleEvenWithGuidance() {
        DailyPlanAiResponse.AiTaskStepDto step = step(
                "Use Java Streams", "Write three examples using Java Streams.",
                AiTaskStepAction.WRITE, "Streams");

        assertThatThrownBy(() -> validator.validate(
                        step,
                        "Practise collection transformation",
                        "Use Java Streams",
                        "Transform collection values with map and filter.",
                        "Modern Java"))
                .isInstanceOf(InvalidAiDailyPlanResponseException.class)
                .hasMessageContaining("merely rename");
    }

    @Test
    void acceptsConcreteActionGroundedInLearningUnitDescription() {
        DailyPlanAiResponse.AiTaskStepDto step = step(
                "Implement a pipeline using map and filter",
                "Run the map pipeline against three sample values.",
                AiTaskStepAction.IMPLEMENT,
                "map");

        assertThatCode(() -> validator.validate(
                        step,
                        "Practise collection transformation",
                        "Use Java Streams",
                        "Transform collection values with map and filter.",
                        "Modern Java"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsTitleOnlyParaphraseOfLearningUnit() {
        DailyPlanAiResponse.AiTaskStepDto step = step(
                "Study Java Streams",
                null,
                AiTaskStepAction.REVIEW,
                "Streams");

        assertThatThrownBy(() -> validator.validate(
                        step,
                        "Practise Java Streams",
                        "Use Java Streams",
                        null,
                        "Modern Java"))
                .isInstanceOf(InvalidAiDailyPlanResponseException.class)
                .hasMessageContaining("merely rename");
    }

    @Test
    void rejectsAnchorOutsideLearningUnitContext() {
        DailyPlanAiResponse.AiTaskStepDto step = step(
                "Implement a Kubernetes deployment",
                null,
                AiTaskStepAction.IMPLEMENT,
                "Kubernetes");

        assertThatThrownBy(() -> validator.validate(
                        step,
                        "Practise Java Streams",
                        "Use Java Streams",
                        "Transform collections with map and filter.",
                        "Modern Java"))
                .isInstanceOf(InvalidAiDailyPlanResponseException.class)
                .hasMessageContaining("outside its supplied Learning Unit context");
    }

    @Test
    void rejectsStepThatDoesNotUseItsDeclaredAnchor() {
        DailyPlanAiResponse.AiTaskStepDto step = step(
                "Implement an unrelated deployment",
                null,
                AiTaskStepAction.IMPLEMENT,
                "Java Streams");

        assertThatThrownBy(() -> validator.validate(
                        step,
                        "Practise collection transformation",
                        "Use Java Streams",
                        null,
                        "Modern Java"))
                .isInstanceOf(InvalidAiDailyPlanResponseException.class)
                .hasMessageContaining("must use its source-grounded scope anchor");
    }

    private DailyPlanAiResponse.AiTaskStepDto step(
            String title,
            String guidance,
            AiTaskStepAction action,
            String scopeAnchor) {
        return new DailyPlanAiResponse.AiTaskStepDto(
                title,
                guidance,
                0,
                10,
                true,
                action,
                scopeAnchor);
    }
}
