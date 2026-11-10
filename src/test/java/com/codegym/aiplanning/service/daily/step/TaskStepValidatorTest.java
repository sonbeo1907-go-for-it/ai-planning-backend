package com.codegym.aiplanning.service.daily.step;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.entity.daily.DailyPlanTaskStep;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskStepValidatorTest {

    private final TaskStepValidator validator = new TaskStepValidator();

    @Test
    void acceptsOrderedRequiredStepsWithinParentBudget() {
        List<DailyPlanTaskStep> steps = List.of(
                step("Write an example", 0, 10, true),
                step("Run the unit tests", 1, 15, true));

        assertThatCode(() -> validator.validateAll(steps, 30, "Practise Optional"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsNormalizedDuplicateTitles() {
        List<DailyPlanTaskStep> steps = List.of(
                step("Run the tests", 0, 10, true),
                step("  RUN   THE TESTS ", 1, 10, true));

        assertThatThrownBy(() -> validator.validateAll(steps, 30, "Practise Optional"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.TASK_STEP_INVALID))
                .hasMessageContaining("unique");
    }

    @Test
    void rejectsStepEstimatesAboveParentTaskTime() {
        List<DailyPlanTaskStep> steps = List.of(
                step("Read the example", 0, 20, true),
                step("Implement the solution", 1, 20, true));

        assertThatThrownBy(() -> validator.validateAll(steps, 30, "Practise Optional"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.TASK_STEP_TIME_EXCEEDED));
    }

    @Test
    void rejectsNonEmptyChecklistWithoutARequiredStep() {
        List<DailyPlanTaskStep> steps = List.of(
                step("Optional extension", 0, 5, false));

        assertThatThrownBy(() -> validator.validateAll(steps, 30, "Practise Optional"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.TASK_STEP_INVALID))
                .hasMessageContaining("at least one required");
    }

    @Test
    void rejectsAStepThatOnlyRepeatsTheParentTaskTitle() {
        List<DailyPlanTaskStep> steps = List.of(
                step("  PRACTISE   OPTIONAL ", 0, 10, true));

        assertThatThrownBy(() -> validator.validateAll(
                        steps,
                        30,
                        "Practise Optional"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.TASK_STEP_INVALID))
                .hasMessageContaining("parent task title");
    }

    private DailyPlanTaskStep step(
            String title,
            int orderIndex,
            Integer estimatedMinutes,
            boolean required) {
        return DailyPlanTaskStep.create(
                UUID.randomUUID(),
                title,
                null,
                orderIndex,
                estimatedMinutes,
                required);
    }
}
