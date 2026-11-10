package com.codegym.aiplanning.controller.roadmap.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

@Schema(description = "Optional source selection for AI Roadmap generation")
public record GenerateRoadmapRequest(
        @Size(max = 10, message = "At most 10 learning materials may be selected")
        @Schema(description = "Ready learning materials owned by the current USER")
        List<UUID> materialIds) {

    public List<UUID> normalizedMaterialIds() {
        return materialIds == null ? List.of() : materialIds.stream().distinct().toList();
    }

    @Override
    public String toString() {
        return "GenerateRoadmapRequest[materialIds=<redacted>]";
    }
}
