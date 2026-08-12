package com.codegym.aiplanning.service.auth.model;

import java.time.Instant;

public record PasswordResetRequestedEvent(
        String email,
        String resetLink,
        Instant requestedAt
) {}
