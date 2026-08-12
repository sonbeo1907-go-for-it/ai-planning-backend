package com.codegym.aiplanning.controller.onboarding.dto;

import com.codegym.aiplanning.entity.roadmap.ProficiencyLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Partial values saved while navigating Roadmap onboarding")
public record SaveRoadmapOnboardingRequest(
        @Schema(
                        description = "User-authored learning topic or goal; treated as untrusted data",
                        example = "Học Lập trình Web với React")
                @Size(max = 500, message = "Goal must not exceed 500 characters")
                String goal,
        @Schema(
                        description = "Current proficiency for this Roadmap",
                        allowableValues = {"BEGINNER", "BASIC", "INTERMEDIATE"})
                ProficiencyLevel proficiencyLevel,
        @Schema(
                        description = "Roadmap-scoped daily commitment in minutes",
                        allowableValues = {"30", "60", "120"},
                        example = "60")
                Integer dailyCommitmentMinutes,
        @Schema(
                        description = "Expected Roadmap duration in days",
                        allowableValues = {"30", "60", "90"},
                        example = "60")
                Integer expectedDurationDays) {

    @Override
    public String toString() {
        return "SaveRoadmapOnboardingRequest[personalLearningData=<redacted>]";
    }
}
