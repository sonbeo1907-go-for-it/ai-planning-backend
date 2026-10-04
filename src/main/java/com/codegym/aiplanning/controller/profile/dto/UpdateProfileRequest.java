package com.codegym.aiplanning.controller.profile.dto;

import com.codegym.aiplanning.common.validation.IanaTimeZone;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@Schema(description = "Editable personal learning profile fields")
public record UpdateProfileRequest(
        @Size(max = 150, message = "Display name must not exceed 150 characters")
                String displayName,
        @IanaTimeZone(message = "TIMEZONE_INVALID")
        @Size(max = 50, message = "Time zone must not exceed 50 characters")
                String timeZone,
        @Size(max = 35, message = "Locale must not exceed 35 characters")
                String locale,
        @Min(value = 15, message = "Default daily minutes must be at least 15")
        @Max(value = 480, message = "Default daily minutes must not exceed 480")
        @Schema(
                description = "Account fallback used when a Roadmap or day does not provide a study-time budget",
                minimum = "15",
                maximum = "480",
                multipleOf = 15,
                example = "60")
                Integer defaultDailyMinutes) {}
