package com.codegym.aiplanning.service.billing.gateway;

public record CheckoutResult(
        String checkoutUrl,
        String checkoutReference,
        String paymentProvider) {}
