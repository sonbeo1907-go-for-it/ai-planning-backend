package com.codegym.aiplanning.controller.course.dto;

import com.codegym.aiplanning.entity.course.ClassStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@Schema(description = "Class search, filtering and pagination parameters")
public record ClassSearchParam(
        @Size(max = 150)
        @Schema(description = "Case-insensitive code or name search", example = "java")
        String search,

        @Schema(description = "Class status filter", example = "ACTIVE")
        ClassStatus status,

        @Min(0)
        @Schema(defaultValue = "0", example = "0")
        Integer page,

        @Min(1)
        @Max(100)
        @Schema(defaultValue = "10", example = "10")
        Integer size) {

    public int resolvedPage() {
        return page == null ? 0 : page;
    }

    public int resolvedSize() {
        return size == null ? 10 : size;
    }
}
