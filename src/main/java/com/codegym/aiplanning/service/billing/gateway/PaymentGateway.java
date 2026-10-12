package com.codegym.aiplanning.service.billing.gateway;

import com.codegym.aiplanning.entity.billing.TopUpOrder;

public interface PaymentGateway {

    CheckoutResult createCheckout(CheckoutRequest request);

    VerifiedPayment verifyNotification(java.util.Map<String, String> parameters);

    VerifiedPayment queryPayment(TopUpOrder order);

    String getProviderName();
}
