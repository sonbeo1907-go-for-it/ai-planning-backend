package com.codegym.aiplanning.controller.roadmap.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body for regenerating an AI Roadmap version with optional feedback")
public record RegenerateRoadmapRequest(
        @Schema(
                description = "Optional user instructions or feedback to adjust the generated roadmap",
                example = "Tập trung thêm vào phần thực hành dự án và rút ngắn phần lý thuyết cơ bản"
        )
        @Size(max = 1000, message = "Adjustment prompt must not exceed 1000 characters")
        String adjustmentPrompt
) {}
