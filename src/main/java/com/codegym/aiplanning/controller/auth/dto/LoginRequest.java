package com.codegym.aiplanning.controller.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Internal account credentials")
public record LoginRequest(
        @Schema(description = "Account email address", example = "admin@aiplanning.local")
                @NotBlank
                @Email
                @Size(max = 254)
                String email,
        @Schema(
                        description = "Account password",
                        example = "Admin@123",
                        accessMode = Schema.AccessMode.WRITE_ONLY)
                @NotBlank
                @Size(max = 200)
                String password) {}
