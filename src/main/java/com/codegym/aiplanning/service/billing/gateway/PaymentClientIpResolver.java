package com.codegym.aiplanning.service.billing.gateway;

import com.codegym.aiplanning.config.BillingProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class PaymentClientIpResolver {
    private final BillingProperties properties;

    public PaymentClientIpResolver(BillingProperties properties) {
        this.properties = properties;
    }

    public String resolve(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        // Never trust client-supplied forwarding headers unless the immediate proxy is configured.
        if (properties.getTrustedProxyAddresses().contains(address)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null) {
                String candidate = forwarded.split(",", 2)[0].trim();
                if (candidate.length() <= 45 && candidate.matches("[0-9a-fA-F:.]+")) {
                    address = candidate;
                }
            }
        }
        return address;
    }
}
