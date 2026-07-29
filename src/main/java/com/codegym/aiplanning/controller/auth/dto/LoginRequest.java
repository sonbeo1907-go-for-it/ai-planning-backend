package com.codegym.aiplanning.controller.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Schema(description = "Internal username issued by the center", example = "admin")
                @NotBlank
                @Size(max = 100)
                String username,
        @Schema(
                        description = "Account password",
                        example = "Admin@123",
                        accessMode = Schema.AccessMode.WRITE_ONLY)
                @NotBlank
                @Size(max = 200)
                String password) {}
