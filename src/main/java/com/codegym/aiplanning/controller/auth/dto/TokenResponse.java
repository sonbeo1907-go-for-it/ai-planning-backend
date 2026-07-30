package com.codegym.aiplanning.controller.auth.dto;

import com.codegym.aiplanning.service.auth.model.AuthToken;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Phản hồi chứa Access Token đăng nhập")
public record TokenResponse(
        @Schema(description = "Chuỗi JWT Access Token")
        String accessToken,

        @Schema(description = "Loại Token (Luôn là Bearer)", example = "Bearer")
        String tokenType,

        @Schema(description = "Thời gian hết hạn của token tính bằng giây", example = "28800")
        long expiresIn) {

    public static TokenResponse from(AuthToken token) {
        return new TokenResponse(token.accessToken(), token.tokenType(), token.expiresIn());
    }
}

