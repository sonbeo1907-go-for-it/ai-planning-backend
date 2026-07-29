package com.codegym.aiplanning.controller.auth.dto;

import com.codegym.aiplanning.service.auth.model.AuthToken;
import io.swagger.v3.oas.annotations.media.Schema;

public record TokenResponse(
        @Schema(description = "Short-lived access JWT", example = "eyJhbGciOiJIUzI1NiJ9...")
                String accessToken,
        @Schema(description = "Authorization scheme", example = "Bearer") String tokenType,
        @Schema(description = "Access-token lifetime in seconds", example = "900")
                long expiresIn) {

    public static TokenResponse from(AuthToken token) {
        return new TokenResponse(token.accessToken(), token.tokenType(), token.expiresIn());
    }
}
