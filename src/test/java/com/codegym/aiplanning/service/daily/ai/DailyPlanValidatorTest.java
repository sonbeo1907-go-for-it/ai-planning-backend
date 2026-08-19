package com.codegym.aiplanning.service.daily.ai;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.codegym.aiplanning.entity.daily.DailyTaskCategory;
import com.codegym.aiplanning.service.ai.AiServiceException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DailyPlanValidatorTest {

    private final DailyPlanValidator validator = new DailyPlanValidator();
    private final DailyPlanningContext context = new DailyPlanningContext(LocalDate.now(), 60, null, null, null, null, null);

    @Test
    void validateResponse_success() {
        DailyPlanAiResponse response = new DailyPlanAiResponse(List.of(
                new DailyPlanAiResponse.AiPlanItemDto(UUID.randomUUID(), "Task 1", null, DailyTaskCategory.CUSTOM, 40, null, null)
        ));
        validator.validateResponse(response, context); // Should not throw exception
    }

    @Test
    void validateResponse_nullResponse_throwsException() {
        assertThatThrownBy(() -> validator.validateResponse(null, context))
                .isInstanceOf(AiServiceException.class);
    }

    @Test
    void validateResponse_negativeMinutes_throwsException() {
        DailyPlanAiResponse response = new DailyPlanAiResponse(List.of(
                new DailyPlanAiResponse.AiPlanItemDto(UUID.randomUUID(), "Task 1", null, DailyTaskCategory.CUSTOM, -10, null, null)
        ));
        assertThatThrownBy(() -> validator.validateResponse(response, context))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("Negative or null minutes");
    }
}
