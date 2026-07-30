package com.codegym.aiplanning.service.auth.model;

public record AuthResult(
        AuthToken accessToken, String refreshToken, long refreshExpiresIn) {}
