package com.codegym.aiplanning.controller.admin.ai.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminAiExecutionFilter(
        Instant from,
        Instant to,
        UUID providerId,
        String model,
        String purpose,
        String operation,
        String status,
        String failureCode
) {}
