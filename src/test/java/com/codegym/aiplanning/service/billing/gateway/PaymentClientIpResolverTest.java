package com.codegym.aiplanning.service.billing.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.codegym.aiplanning.config.BillingProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class PaymentClientIpResolverTest {

    @Test
    void ignoresForwardedHeadersFromUntrustedClients() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-For", "198.51.100.20");

        var resolver = new PaymentClientIpResolver(new BillingProperties());

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
    }

    @Test
    void acceptsTheCustomerAddressOnlyFromAConfiguredProxy() {
        var properties = new BillingProperties();
        properties.setTrustedProxyAddresses(List.of("127.0.0.1"));
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "198.51.100.20, 127.0.0.1");

        assertThat(new PaymentClientIpResolver(properties).resolve(request))
                .isEqualTo("198.51.100.20");
    }

    @Test
    void rejectsMalformedForwardedValuesWithoutResolvingHostnames() {
        var properties = new BillingProperties();
        properties.setTrustedProxyAddresses(List.of("127.0.0.1"));
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "untrusted.example/path");

        assertThat(new PaymentClientIpResolver(properties).resolve(request))
                .isEqualTo("127.0.0.1");
    }
}
