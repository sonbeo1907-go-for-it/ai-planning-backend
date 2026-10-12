package com.codegym.aiplanning.service.billing.gateway;

/** Only a gateway adapter may construct this after verifying the provider signature. */
public record VerifiedPayment(
        String provider,
        String orderCode,
        String transactionId,
        long amountMinor,
        String responseCode,
        String transactionStatus,
        boolean queryVerified) {

    public boolean succeeded() {
        return "00".equals(responseCode) && "00".equals(transactionStatus);
    }
}
