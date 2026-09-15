package com.codegym.aiplanning.service.daily.step;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.controller.daily.dto.DailyPlanTaskStepsResponse;
import com.codegym.aiplanning.entity.daily.DailyPlanTaskStep;
import com.codegym.aiplanning.entity.daily.DailyPlanTaskStepState;
import com.codegym.aiplanning.repository.daily.DailyPlanTaskStepRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanTaskStepStateRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TaskStepReadModelBuilderTest {

    @Mock
    private DailyPlanTaskStepRepository taskStepRepository;

    @Mock
    private DailyPlanTaskStepStateRepository taskStepStateRepository;

    @Test
    void buildForItems_fetchesAllStepsAndStatesInTwoBatchQueries() {
        UUID firstItemId = UUID.randomUUID();
        UUID secondItemId = UUID.randomUUID();
        DailyPlanTaskStep firstStep = step(firstItemId, "First action", 0);
        DailyPlanTaskStep secondStep = step(secondItemId, "Second action", 0);
        DailyPlanTaskStepState firstState = DailyPlanTaskStepState.create(firstStep.getId());
        firstState.setCompleted(true, Instant.parse("2032-01-01T10:00:00Z"));

        List<UUID> itemIds = List.of(firstItemId, secondItemId);
        List<UUID> stepIds = List.of(firstStep.getId(), secondStep.getId());
        when(taskStepRepository.findByDailyPlanItemIdInOrderByItemAndOrder(itemIds))
                .thenReturn(List.of(firstStep, secondStep));
        when(taskStepStateRepository.findByTaskStepIdIn(stepIds))
                .thenReturn(List.of(firstState));

        TaskStepReadModelBuilder builder = new TaskStepReadModelBuilder(
                taskStepRepository,
                taskStepStateRepository);
        Map<UUID, DailyPlanTaskStepsResponse> result = builder.buildForItems(itemIds);

        assertThat(result).hasSize(2);
        assertThat(result.get(firstItemId).progress().completionPercentage())
                .isEqualTo(100.0);
        assertThat(result.get(secondItemId).progress().completionPercentage())
                .isZero();
        verify(taskStepRepository).findByDailyPlanItemIdInOrderByItemAndOrder(itemIds);
        verify(taskStepStateRepository).findByTaskStepIdIn(stepIds);
    }

    private DailyPlanTaskStep step(UUID itemId, String title, int orderIndex) {
        DailyPlanTaskStep step = DailyPlanTaskStep.create(
                itemId,
                title,
                null,
                orderIndex,
                10,
                true);
        ReflectionTestUtils.setField(step, "id", UUID.randomUUID());
        return step;
    }
}
