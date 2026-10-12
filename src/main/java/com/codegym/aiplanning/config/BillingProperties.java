package com.codegym.aiplanning.config;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.billing")
public class BillingProperties {

    @PositiveOrZero
    private long welcomeCredits = 1000L;
    private boolean paymentsEnabled;
    private java.util.List<String> trustedProxyAddresses = java.util.List.of();

    public java.util.List<String> getTrustedProxyAddresses() {
        return trustedProxyAddresses;
    }

    public void setTrustedProxyAddresses(java.util.List<String> trustedProxyAddresses) {
        this.trustedProxyAddresses = trustedProxyAddresses;
    }
    @Valid
    private TopUpProperties topUp = new TopUpProperties();
    @Valid
    private VnpayProperties vnpay = new VnpayProperties();

    public boolean isPaymentsEnabled() {
        return paymentsEnabled;
    }

    public void setPaymentsEnabled(boolean paymentsEnabled) {
        this.paymentsEnabled = paymentsEnabled;
    }

    @AssertTrue(message = "Enabled payments require explicit VNPAY credentials, HTTPS gateway URLs and a return URL")
    public boolean isPaymentConfigurationValid() {
        return !paymentsEnabled || isGatewayConfigurationValid();
    }

    public boolean isGatewayConfigurationValid() {
        return vnpay != null
                && vnpay.tmnCode != null && !vnpay.tmnCode.isBlank()
                && !"VNPAYTMN".equals(vnpay.tmnCode)
                && vnpay.hashSecret != null && !vnpay.hashSecret.isBlank()
                && !"SECRETKEY123".equals(vnpay.hashSecret)
                && isHttpsUrl(vnpay.paymentUrl) && isHttpsUrl(vnpay.queryUrl)
                && isReturnUrl(vnpay.returnUrl);
    }

    private boolean isHttpsUrl(String value) {
        try {
            var uri = java.net.URI.create(value);
            return "https".equals(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean isReturnUrl(String value) {
        if (isHttpsUrl(value)) return true;
        try {
            var uri = java.net.URI.create(value);
            return "http".equals(uri.getScheme()) && "localhost".equals(uri.getHost())
                    && uri.getUserInfo() == null;
        } catch (Exception exception) {
            return false;
        }
    }

    public BillingProperties() {}

    public BillingProperties(long welcomeCredits) {
        this.welcomeCredits = welcomeCredits;
    }

    public long getWelcomeCredits() {
        return welcomeCredits;
    }

    public long welcomeCredits() {
        return welcomeCredits;
    }

    public void setWelcomeCredits(long welcomeCredits) {
        this.welcomeCredits = welcomeCredits;
    }

    public TopUpProperties getTopUp() {
        return topUp;
    }

    public void setTopUp(TopUpProperties topUp) {
        this.topUp = topUp;
    }

    public VnpayProperties getVnpay() {
        return vnpay;
    }

    public void setVnpay(VnpayProperties vnpay) {
        this.vnpay = vnpay;
    }

    public static class TopUpProperties {
        @Positive
        private long minVnd = 20_000L;
        @Positive
        private long maxVnd = 2_000_000L;
        @Positive
        private long expirationMinutes = 15L;

        @AssertTrue(message = "Top-up minimum must not exceed maximum")
        public boolean isRangeValid() {
            return minVnd <= maxVnd;
        }

        public long getMinVnd() {
            return minVnd;
        }

        public void setMinVnd(long minVnd) {
            this.minVnd = minVnd;
        }

        public long getMaxVnd() {
            return maxVnd;
        }

        public void setMaxVnd(long maxVnd) {
            this.maxVnd = maxVnd;
        }

        public long getExpirationMinutes() {
            return expirationMinutes;
        }

        public void setExpirationMinutes(long expirationMinutes) {
            this.expirationMinutes = expirationMinutes;
        }
    }

    public static class VnpayProperties {
        private String paymentUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
        private String tmnCode = "";
        private String hashSecret = "";
        private String returnUrl = "http://localhost:3000/billing/checkout/result";
        private String queryUrl = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";
        private String serverIp = "127.0.0.1";
        @Positive
        private int timeoutSeconds = 10;

        public String getQueryUrl() {
            return queryUrl;
        }

        public void setQueryUrl(String queryUrl) {
            this.queryUrl = queryUrl;
        }

        public String getServerIp() {
            return serverIp;
        }

        public void setServerIp(String serverIp) {
            this.serverIp = serverIp;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public String getPaymentUrl() {
            return paymentUrl;
        }

        public void setPaymentUrl(String paymentUrl) {
            this.paymentUrl = paymentUrl;
        }

        public String getTmnCode() {
            return tmnCode;
        }

        public void setTmnCode(String tmnCode) {
            this.tmnCode = tmnCode;
        }

        public String getHashSecret() {
            return hashSecret;
        }

        public void setHashSecret(String hashSecret) {
            this.hashSecret = hashSecret;
        }

        public String getReturnUrl() {
            return returnUrl;
        }

        public void setReturnUrl(String returnUrl) {
            this.returnUrl = returnUrl;
        }
    }
}
