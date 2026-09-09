package com.codegym.aiplanning.controller.daily.dto;

import com.codegym.aiplanning.entity.daily.DailyTaskCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(description = "Update a task belonging to one exact DRAFT Daily Plan version")
public record UpdateDailyTaskRequest(
        @NotBlank(message = "Task title is required")
        @Size(max = 255, message = "Task title must not exceed 255 characters")
        String title,

        @Size(max = 4000, message = "Task description must not exceed 4000 characters")
        String description,

        @NotNull(message = "Task category is required")
        DailyTaskCategory category,

        @NotNull(message = "Planned minutes are required")
        @Min(value = 1, message = "Planned minutes must be at least 1")
        @Max(value = 1440, message = "Planned minutes cannot exceed 1440")
        Integer plannedMinutes,

        @NotNull(message = "Order index is required")
        @Min(value = 0, message = "Order index must not be negative")
        Integer orderIndex,

        UUID roadmapItemId) {

    public UpdateDailyTaskRequest(
            String title,
            String description,
            DailyTaskCategory category,
            Integer plannedMinutes,
            Integer orderIndex) {
        this(title, description, category, plannedMinutes, orderIndex, null);
    }

    @Override
    public String toString() {
        return "UpdateDailyTaskRequest[category=" + category
                + ", plannedMinutes=" + plannedMinutes
                + ", orderIndex=" + orderIndex
                + ", personalLearningData=<redacted>]";
    }
}
