package com.codegym.aiplanning.config;

import static org.assertj.core.api.Assertions.assertThat;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class BillingPropertiesTest {
    @Test
    void paymentsCanRemainDisabledWithoutCredentials() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(new BillingProperties())).isEmpty();
        }
    }

    @Test
    void enabledPaymentsRejectMissingAndPlaceholderCredentials() {
        BillingProperties properties = new BillingProperties();
        properties.setPaymentsEnabled(true);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(properties)).isNotEmpty();
            properties.getVnpay().setTmnCode("VNPAYTMN");
            properties.getVnpay().setHashSecret("SECRETKEY123");
            assertThat(factory.getValidator().validate(properties)).isNotEmpty();
        }
    }
}
