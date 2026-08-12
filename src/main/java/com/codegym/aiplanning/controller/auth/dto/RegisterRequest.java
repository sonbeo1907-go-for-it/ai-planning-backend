package com.codegym.aiplanning.controller.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Public local-account registration request")
public record RegisterRequest(
        @Schema(
                        description = "Email address used to sign in",
                        example = "user@example.com",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank(message = "Email is required")
                @Email(message = "Email must be valid")
                @Size(max = 254, message = "Email must not exceed 254 characters")
                String email,
        @Schema(
                        description = "Password: 8-100 characters with uppercase, lowercase and a digit",
                        example = "Password@123",
                        accessMode = Schema.AccessMode.WRITE_ONLY,
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank(message = "Password is required")
                @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
                @Pattern(
                        regexp = ".*[a-z].*",
                        message = "Password must contain a lowercase letter")
                @Pattern(
                        regexp = ".*[A-Z].*",
                        message = "Password must contain an uppercase letter")
                @Pattern(
                        regexp = ".*\\d.*",
                        message = "Password must contain a digit")
                String password,
                @Schema(
                        description = "User display name",
                        example = "Nguyen Van A",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank(message = "Display name is required")
                @Size(max = 150, message = "Display name must not exceed 150 characters")
                String displayName) {}
