package com.codegym.aiplanning.controller.billing.dto;

import com.codegym.aiplanning.entity.billing.CreditPackage;
import java.util.UUID;

public record CreditPackageResponse(
        UUID id,
        String packageCode,
        String name,
        long priceVnd,
        long baseCredits,
        long bonusCredits,
        long totalCredits,
        String status) {

    public static CreditPackageResponse from(CreditPackage pkg) {
        return new CreditPackageResponse(
                pkg.getId(),
                pkg.getPackageCode(),
                pkg.getName(),
                pkg.getPriceVnd(),
                pkg.getBaseCredits(),
                pkg.getBonusCredits(),
                pkg.getTotalCredits(),
                pkg.getStatus().name());
    }
}
