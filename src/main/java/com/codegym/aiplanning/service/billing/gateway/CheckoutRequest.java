package com.codegym.aiplanning.service.billing.gateway;

import com.codegym.aiplanning.entity.billing.TopUpOrder;

public record CheckoutRequest(TopUpOrder order, String clientIp) {}
