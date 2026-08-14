package com.codegym.aiplanning.controller.material.dto;

import com.codegym.aiplanning.entity.material.ExtractionErrorCode;
import com.codegym.aiplanning.entity.material.Material;
import com.codegym.aiplanning.entity.material.MaterialStatus;
import com.codegym.aiplanning.entity.material.MaterialType;
import java.time.Instant;
import java.util.UUID;

public record MaterialListResponse(
        UUID id,
        MaterialType type,
        MaterialStatus status,
        String originalFileName,
        String contentType,
        Long fileSize,
        ExtractionErrorCode errorCode,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt) {

    public static MaterialListResponse from(Material material) {
        return new MaterialListResponse(
                material.getId(),
                material.getType(),
                material.getStatus(),
                material.getOriginalFileName(),
                material.getContentType(),
                material.getFileSize(),
                material.getErrorCode(),
                material.getErrorMessage(),
                material.getCreatedAt(),
                material.getUpdatedAt());
    }
}
