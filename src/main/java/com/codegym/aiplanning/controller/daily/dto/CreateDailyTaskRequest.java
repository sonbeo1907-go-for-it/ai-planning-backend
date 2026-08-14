package com.codegym.aiplanning.controller.daily.dto;

import com.codegym.aiplanning.entity.daily.DailyTaskCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

@Schema(description = "Request to add a manual task to a daily plan (US-TSK-01-MANUAL)")
public record CreateDailyTaskRequest(
        @NotBlank(message = "Task title is required")
        @Schema(description = "Title of the learning task", example = "Học lập trình Java Core Module 1")
        String title,

        @Schema(description = "Detailed task description or notes", example = "Đọc tài liệu Java String & Arrays")
        String description,

        @Schema(description = "Category of the task", example = "CUSTOM")
        DailyTaskCategory category,

        @Min(value = 1, message = "Planned minutes must be at least 1")
        @Max(value = 1440, message = "Planned minutes cannot exceed 1440")
        @Schema(description = "Estimated time in minutes", example = "30")
        Integer plannedMinutes,

        @Schema(description = "Associated Roadmap Item ID", example = "594c4df0-7f4d-4cb4-81c4-70735b0db2bf")
        UUID roadmapItemId
) {}
