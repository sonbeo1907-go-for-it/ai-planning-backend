package com.codegym.aiplanning.service.daily.step;

import com.codegym.aiplanning.controller.daily.dto.DailyPlanTaskStepResponse;
import com.codegym.aiplanning.controller.daily.dto.DailyPlanTaskStepsResponse;
import com.codegym.aiplanning.controller.daily.dto.TaskStepProgressResponse;
import com.codegym.aiplanning.entity.daily.DailyPlanTaskStep;
import com.codegym.aiplanning.entity.daily.DailyPlanTaskStepState;
import com.codegym.aiplanning.repository.daily.DailyPlanTaskStepRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanTaskStepStateRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class TaskStepReadModelBuilder {

    private final DailyPlanTaskStepRepository taskStepRepository;
    private final DailyPlanTaskStepStateRepository taskStepStateRepository;

    public TaskStepReadModelBuilder(
            DailyPlanTaskStepRepository taskStepRepository,
            DailyPlanTaskStepStateRepository taskStepStateRepository) {
        this.taskStepRepository = taskStepRepository;
        this.taskStepStateRepository = taskStepStateRepository;
    }

    public DailyPlanTaskStepsResponse buildForItem(UUID itemId) {
        return buildForItems(List.of(itemId)).get(itemId);
    }

    public Map<UUID, DailyPlanTaskStepsResponse> buildForItems(
            Collection<UUID> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return Map.of();
        }

        List<UUID> distinctItemIds = itemIds.stream().distinct().toList();
        List<DailyPlanTaskStep> steps = taskStepRepository
                .findByDailyPlanItemIdInOrderByItemAndOrder(distinctItemIds);
        List<UUID> stepIds = steps.stream()
                .map(DailyPlanTaskStep::getId)
                .toList();
        Map<UUID, DailyPlanTaskStepState> stateByStepId = stepIds.isEmpty()
                ? Map.of()
                : taskStepStateRepository.findByTaskStepIdIn(stepIds).stream()
                        .collect(Collectors.toMap(
                                DailyPlanTaskStepState::getTaskStepId,
                                Function.identity()));

        Map<UUID, List<DailyPlanTaskStep>> stepsByItemId = new LinkedHashMap<>();
        for (DailyPlanTaskStep step : steps) {
            stepsByItemId
                    .computeIfAbsent(
                            step.getDailyPlanItemId(),
                            ignored -> new ArrayList<>())
                    .add(step);
        }

        Map<UUID, DailyPlanTaskStepsResponse> result = new LinkedHashMap<>();
        for (UUID itemId : distinctItemIds) {
            result.put(
                    itemId,
                    buildResponse(
                            itemId,
                            stepsByItemId.getOrDefault(itemId, List.of()),
                            stateByStepId));
        }
        return result;
    }

    private DailyPlanTaskStepsResponse buildResponse(
            UUID itemId,
            List<DailyPlanTaskStep> steps,
            Map<UUID, DailyPlanTaskStepState> stateByStepId) {
        List<DailyPlanTaskStepResponse> responses = steps.stream()
                .map(step -> DailyPlanTaskStepResponse.from(
                        step,
                        stateByStepId.get(step.getId())))
                .toList();

        int requiredCount = 0;
        int completedRequiredCount = 0;
        for (DailyPlanTaskStep step : steps) {
            if (!Boolean.TRUE.equals(step.getRequired())) {
                continue;
            }
            requiredCount++;
            DailyPlanTaskStepState state = stateByStepId.get(step.getId());
            if (state != null && Boolean.TRUE.equals(state.getCompleted())) {
                completedRequiredCount++;
            }
        }

        double percentage = requiredCount == 0
                ? 0.0
                : completedRequiredCount * 100.0 / requiredCount;
        TaskStepProgressResponse progress = new TaskStepProgressResponse(
                requiredCount,
                completedRequiredCount,
                percentage,
                requiredCount > 0 && completedRequiredCount == requiredCount);
        return new DailyPlanTaskStepsResponse(itemId, responses, progress);
    }
}
