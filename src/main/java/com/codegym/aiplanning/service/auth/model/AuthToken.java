package com.codegym.aiplanning.service.auth.model;

public record AuthToken(String accessToken, String tokenType, long expiresIn) {}
