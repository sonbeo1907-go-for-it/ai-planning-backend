package com.codegym.aiplanning.controller.guidance.dto;

import com.codegym.aiplanning.entity.guidance.GuidanceReferenceProvenance;
import java.util.UUID;

public record TaskGuidanceReferenceResponse(
        UUID id,
        GuidanceReferenceProvenance provenance,
        String displayLabel,
        String locator,
        UUID targetId,
        String externalUrl,
        boolean unverified) {}
