package com.codegym.aiplanning.controller.billing.dto;

import com.codegym.aiplanning.entity.ai.AiPurpose;
import com.codegym.aiplanning.entity.billing.AiCreditRate;
import java.util.UUID;

public record AiCreditRateResponse(
        UUID id,
        AiPurpose purpose,
        String modelCategory,
        long creditCost
) {
    public static AiCreditRateResponse from(AiCreditRate rate) {
        return new AiCreditRateResponse(
                rate.getId(),
                rate.getPurpose(),
                rate.getModelCategory(),
                rate.getCreditCost()
        );
    }
}
