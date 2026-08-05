package com.codegym.aiplanning.controller.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Google Identity Services credential")
public record GoogleLoginRequest(
        @Schema(
                        description = "Google-signed OpenID Connect ID token returned in the "
                                + "Google Identity Services credential field",
                        format = "password")
                @NotBlank(message = "Google ID token is required")
                @Size(max = 8192, message = "Google ID token is too long")
                String idToken) {}
