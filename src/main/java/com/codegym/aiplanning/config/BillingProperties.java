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
}
