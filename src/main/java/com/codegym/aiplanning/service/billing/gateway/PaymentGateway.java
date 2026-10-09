package com.codegym.aiplanning.service.billing.gateway;

import com.codegym.aiplanning.entity.billing.TopUpOrder;

public interface PaymentGateway {

    CheckoutResult createCheckout(TopUpOrder order);

    String getProviderName();
}
