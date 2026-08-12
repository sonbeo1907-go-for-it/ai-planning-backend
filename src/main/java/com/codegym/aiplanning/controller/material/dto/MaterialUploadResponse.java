package com.codegym.aiplanning.controller.material.dto;

import java.time.Instant;
import java.util.UUID;

public record MaterialUploadResponse(
        UUID id,
        String originalFileName,
        String contentType,
        Long fileSize,
        Instant createdAt
) {
}
