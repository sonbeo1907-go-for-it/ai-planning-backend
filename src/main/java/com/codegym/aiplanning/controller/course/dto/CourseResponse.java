package com.codegym.aiplanning.controller.course.dto;

import com.codegym.aiplanning.entity.course.Course;
import com.codegym.aiplanning.entity.course.CourseStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Course details")
public record CourseResponse(
        UUID id,
        String code,
        String name,
        String description,
        CourseStatus status,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy,
        long version) {

    public static CourseResponse from(Course course) {
        return new CourseResponse(
                course.getId(),
                course.getCode(),
                course.getName(),
                course.getDescription(),
                course.getStatus(),
                course.getCreatedAt(),
                course.getUpdatedAt(),
                course.getCreatedBy(),
                course.getUpdatedBy(),
                course.getVersion());
    }
}
