package com.codegym.aiplanning.controller.curriculum.dto;

import com.codegym.aiplanning.entity.curriculum.Module;
import com.codegym.aiplanning.entity.curriculum.ModuleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Module details (US-MOD-01)")
public record ModuleResponse(
        UUID id,
        UUID courseId,
        String code,
        String name,
        String description,
        Integer sequenceNumber,
        ModuleStatus status,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy,
        long version) {

    public static ModuleResponse from(Module module) {
        return new ModuleResponse(
                module.getId(),
                module.getCourseId(),
                module.getCode(),
                module.getName(),
                module.getDescription(),
                module.getSequenceNumber(),
                module.getStatus(),
                module.getCreatedAt(),
                module.getUpdatedAt(),
                module.getCreatedBy(),
                module.getUpdatedBy(),
                module.getVersion());
    }
}
