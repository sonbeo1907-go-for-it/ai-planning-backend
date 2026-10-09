package com.codegym.aiplanning.controller.billing.dto;

import com.codegym.aiplanning.entity.billing.TopUpOrder;
import java.time.Instant;
import java.util.UUID;

public record TopUpOrderResponse(
        UUID id,
        String orderCode,
        String packageCode,
        String packageName,
        long priceVnd,
        long baseCredits,
        long bonusCredits,
        long totalCredits,
        String status,
        String currency,
        String paymentProvider,
        String checkoutUrl,
        Instant expiresAt,
        Instant createdAt) {

    public static TopUpOrderResponse from(TopUpOrder order, String checkoutUrl) {
        return new TopUpOrderResponse(
                order.getId(),
                order.getOrderCode(),
                order.getPackageCodeSnapshot(),
                order.getPackageNameSnapshot(),
                order.getPriceVndSnapshot(),
                order.getBaseCreditsSnapshot(),
                order.getBonusCreditsSnapshot(),
                order.getTotalCreditsSnapshot(),
                order.getStatus().name(),
                order.getCurrency(),
                order.getPaymentProvider(),
                checkoutUrl,
                order.getExpiresAt(),
                order.getCreatedAt());
    }
}
