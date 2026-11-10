package com.codegym.aiplanning.controller.guidance.dto;

import jakarta.validation.constraints.Size;

public record RegenerateTaskGuidanceRequest(
        @Size(max = 1000) String adjustmentInstruction) {

    public String normalizedAdjustmentInstruction() {
        return adjustmentInstruction == null || adjustmentInstruction.isBlank()
                ? null
                : adjustmentInstruction.strip();
    }
}
