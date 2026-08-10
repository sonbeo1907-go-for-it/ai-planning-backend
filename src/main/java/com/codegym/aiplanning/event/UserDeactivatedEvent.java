package com.codegym.aiplanning.event;

import com.codegym.aiplanning.entity.auth.DeactivationReasonCode;
import java.util.UUID;

public record UserDeactivatedEvent(UUID userId, DeactivationReasonCode reasonCode, String publicReason) {}
