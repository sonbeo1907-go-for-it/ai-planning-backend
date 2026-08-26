package com.codegym.aiplanning.controller.evaluation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record QuizOptionDto(
        @Schema(example = "A")
        String key,

        @Schema(example = "Khai báo một RESTful Controller trong Spring")
        String text
) {}
