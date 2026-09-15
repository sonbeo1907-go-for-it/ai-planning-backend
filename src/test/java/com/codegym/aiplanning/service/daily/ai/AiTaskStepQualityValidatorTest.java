package com.codegym.aiplanning.service.daily.ai;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AiTaskStepQualityValidatorTest {

    private final AiTaskStepQualityValidator validator =
            new AiTaskStepQualityValidator();

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
