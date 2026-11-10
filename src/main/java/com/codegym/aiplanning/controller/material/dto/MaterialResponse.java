package com.codegym.aiplanning.controller.material.dto;

import com.codegym.aiplanning.entity.material.MaterialStatus;
import com.codegym.aiplanning.entity.material.MaterialType;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MaterialResponse(
        UUID id,
        String originalFileName,
        String contentType,
        Long fileSize,
        Instant createdAt,
        MaterialType type,
        MaterialStatus status,
        String content,
        com.codegym.aiplanning.entity.material.ExtractionErrorCode errorCode,
        String errorMessage
) {
}
