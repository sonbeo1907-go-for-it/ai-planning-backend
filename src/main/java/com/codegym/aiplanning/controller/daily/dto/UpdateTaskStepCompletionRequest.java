package com.codegym.aiplanning.controller.daily.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Idempotently set the runtime completion state of one Task Step")
public record UpdateTaskStepCompletionRequest(
        @NotNull(message = "Completed flag is required")
        Boolean completed,

        @Min(value = 0, message = "Task Step state version must not be negative")
        Long stateVersion) {}
