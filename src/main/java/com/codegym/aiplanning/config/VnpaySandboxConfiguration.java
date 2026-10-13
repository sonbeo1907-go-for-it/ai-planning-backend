package com.codegym.aiplanning.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Fail closed if an external override points the sandbox run at a live gateway. */
@Configuration(proxyBeanMethods = false)
@Profile("sandbox")
public class VnpaySandboxConfiguration {

    private static final String PAYMENT_URL =
            "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    private static final String QUERY_URL =
            "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";

    @Bean
    InitializingBean verifyVnpaySandboxEndpoints(BillingProperties properties) {
        return () -> {
            var gateway = properties.getVnpay();
            if (!PAYMENT_URL.equals(gateway.getPaymentUrl())
                    || !QUERY_URL.equals(gateway.getQueryUrl())) {
                throw new IllegalStateException(
                        "The sandbox profile requires VNPay sandbox payment and query URLs.");
            }
        };
    }
}
