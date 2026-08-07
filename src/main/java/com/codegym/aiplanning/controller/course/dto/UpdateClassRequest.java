package com.codegym.aiplanning.controller.course.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

@Schema(description = "Payload for updating mutable class details")
public record UpdateClassRequest(
        @NotBlank
        @Size(max = 150)
        @Schema(example = "Fullstack Java Advanced K1")
        String name,

        @Size(max = 4000)
        @Schema(description = "Detailed class description")
        String description,

        @Schema(description = "Class opening date", example = "2024-01-01T00:00:00Z")
        Instant openedAt,

        @Schema(description = "Class closing date", example = "2024-06-01T00:00:00Z")
        Instant closedAt,

        @NotNull
        @Schema(description = "Entity version for optimistic locking", example = "1")
        Long version) {}
