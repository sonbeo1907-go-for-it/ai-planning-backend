package com.codegym.aiplanning.controller.course.dto;

import com.codegym.aiplanning.entity.course.ClassStatus;
import com.codegym.aiplanning.entity.course.StudyClass;
import java.time.Instant;
import java.util.UUID;

public record ClassResponse(
        UUID id,
        UUID courseId,
        String code,
        String name,
        String description,
        ClassStatus status,
        Instant openedAt,
        Instant closedAt,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy,
        Long version) {

    public static ClassResponse from(StudyClass studyClass) {
        return new ClassResponse(
                studyClass.getId(),
                studyClass.getCourseId(),
                studyClass.getCode(),
                studyClass.getName(),
                studyClass.getDescription(),
                studyClass.getStatus(),
                studyClass.getOpenedAt(),
                studyClass.getClosedAt(),
                studyClass.getCreatedAt(),
                studyClass.getUpdatedAt(),
                studyClass.getCreatedBy(),
                studyClass.getUpdatedBy(),
                studyClass.getVersion());
    }
}
