package com.codegym.aiplanning.controller.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Required values for first-access profile setup")
public record CompleteProfileSetupRequest(
        @Schema(example = "Vũ Ngọc Duy", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank(message = "Display name is required")
                @Size(max = 150, message = "Display name must not exceed 150 characters")
                String displayName,
        @Schema(
                        description = "IANA time-zone ID detected by the browser and confirmed by the user",
                        example = "Asia/Ho_Chi_Minh",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank(message = "Time zone is required")
                @Size(max = 50, message = "Time zone must not exceed 50 characters")
                String timeZone,
        @Schema(
                        description = "BCP 47 system-language tag",
                        example = "vi",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank(message = "Locale is required")
                @Size(max = 35, message = "Locale must not exceed 35 characters")
                String locale,
        @Min(value = 1, message = "Default daily minutes must be at least 1")
        @Max(value = 1440, message = "Default daily minutes must not exceed 1440")
                Integer defaultDailyMinutes) {}
