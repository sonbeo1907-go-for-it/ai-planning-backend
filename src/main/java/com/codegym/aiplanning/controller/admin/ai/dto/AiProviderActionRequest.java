package com.codegym.aiplanning.controller.admin.ai.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AiProviderActionRequest(@NotNull @Min(0) Long version) {}
