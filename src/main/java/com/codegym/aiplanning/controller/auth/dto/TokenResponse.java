package com.codegym.aiplanning.controller.auth.dto;

import com.codegym.aiplanning.service.auth.model.AuthToken;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

    public static TokenResponse from(AuthToken token) {
        return new TokenResponse(token.accessToken(), token.tokenType(), token.expiresIn());
    }
}
