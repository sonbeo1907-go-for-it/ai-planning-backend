package com.codegym.aiplanning.controller.user.dto;

import com.codegym.aiplanning.common.validation.user.ValidDeactivateReason;
import com.codegym.aiplanning.entity.auth.DeactivationReasonCode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@ValidDeactivateReason
public record DeactivateUserRequest(
    @NotNull(message = "Reason code is required")
    DeactivationReasonCode reasonCode,
    @Size(max = 500, message = "Public reason must not exceed 500 characters")
    String publicReason,

    @Size(max = 500, message = "Reason note must not exceed 500 characters")
    String reasonNote
) {}
