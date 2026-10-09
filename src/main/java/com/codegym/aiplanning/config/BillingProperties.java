package com.codegym.aiplanning.config;

import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.billing")
public class BillingProperties {

    @PositiveOrZero
    private long welcomeCredits = 1000L;
    private TopUpProperties topUp = new TopUpProperties();
    private VnpayProperties vnpay = new VnpayProperties();

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
        private long minVnd = 20_000L;
        private long maxVnd = 2_000_000L;
        private long expirationMinutes = 15L;

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
        private String tmnCode = "VNPAYTMN";
        private String hashSecret = "SECRETKEY123";
        private String returnUrl = "http://localhost:3000/billing/checkout/result";

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
