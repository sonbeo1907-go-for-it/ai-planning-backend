package com.codegym.aiplanning.controller.daily.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Atomically completes one Task Step and records the parent task outcome")
public record CompleteTaskStepAndRecordProgressRequest(
        @Schema(description = "Optimistic-lock version of the runtime step state; null when no state exists")
        Long stateVersion,

        @NotNull(message = "Outcome is required")
        @Valid
        RecordProgressRequest outcome) {}
