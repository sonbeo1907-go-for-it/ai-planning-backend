package com.codegym.aiplanning.controller.billing.dto;

import java.util.UUID;

public record CreditWalletResponse(
        UUID id,
        long availableCredits,
        long reservedCredits) {}
