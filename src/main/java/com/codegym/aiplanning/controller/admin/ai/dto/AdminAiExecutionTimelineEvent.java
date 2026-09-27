package com.codegym.aiplanning.controller.admin.ai.dto;

import java.time.Instant;

public record AdminAiExecutionTimelineEvent(
        String eventName,
        Instant timestamp,
        boolean synthetic
) {}
