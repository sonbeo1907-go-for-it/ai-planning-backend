package com.codegym.aiplanning.controller.curriculum.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Schema(description = "Payload for reordering course modules sequence numbers (US-MOD-03)")
public record ReorderModulesRequest(
        @NotEmpty
        @Valid
        @Schema(description = "Danh sách thứ tự mới cho các Module")
        List<ModuleOrderItem> items) {}
