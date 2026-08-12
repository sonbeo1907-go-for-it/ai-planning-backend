package com.codegym.aiplanning.controller.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@Schema(description = "Editable personal learning profile fields")
public record UpdateProfileRequest(
        @Size(max = 150, message = "Display name must not exceed 150 characters")
                String displayName,
        @Size(max = 50, message = "Time zone must not exceed 50 characters")
                String timeZone,
        @Size(max = 35, message = "Locale must not exceed 35 characters")
                String locale,
        @Min(value = 1, message = "Default daily minutes must be at least 1")
        @Max(value = 1440, message = "Default daily minutes must not exceed 1440")
                Integer defaultDailyMinutes) {}
