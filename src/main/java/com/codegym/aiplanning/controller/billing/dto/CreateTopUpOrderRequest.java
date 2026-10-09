package com.codegym.aiplanning.controller.billing.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateTopUpOrderRequest(
        @NotNull(message = "packageId is required")
        UUID packageId) {}
