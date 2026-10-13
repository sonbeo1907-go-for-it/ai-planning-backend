package com.codegym.aiplanning.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class VnpaySandboxConfigurationTest {

    @TempDir
    Path temporaryDirectory;

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> context.getEnvironment().setActiveProfiles("sandbox"))
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void disabledSandboxStartsWithoutCredentials() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(BillingProperties.class).isPaymentsEnabled()).isFalse();
        });
    }

    @Test
    void enabledSandboxRequiresCredentials() {
        runner.withPropertyValues("app.billing.payments-enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void enabledSandboxStartsWithTestCredentials() {
        runner.withPropertyValues(
                "app.billing.payments-enabled=true",
                "app.billing.vnpay.tmn-code=TESTTMN1",
                "app.billing.vnpay.hash-secret=test-only-signing-key")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void sandboxRejectsLiveCheckoutOverrideEvenWhenPurchasesAreDisabled() {
        runner.withPropertyValues("app.billing.vnpay.payment-url=https://pay.vnpay.vn/vpcpay.html")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage(
                            "The sandbox profile requires VNPay sandbox payment and query URLs.");
                });
    }

    @Test
    void sandboxRejectsLiveQueryOverride() {
        runner.withPropertyValues("app.billing.vnpay.query-url=https://pay.vnpay.vn/query")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void guardDoesNotChangeNonSandboxProfiles() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestConfiguration.class)
                .withPropertyValues("app.billing.vnpay.payment-url=https://pay.vnpay.vn/vpcpay.html")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("verifyVnpaySandboxEndpoints");
                });
    }

    @Test
    void configDataLoadsAnExtensionlessSandboxPropertiesFile() throws Exception {
        Path file = temporaryDirectory.resolve(".env.sandbox");
        Files.writeString(file, """
                BILLING_PAYMENTS_ENABLED=true
                VNPAY_TMN_CODE=TESTTMN1
                VNPAY_HASH_SECRET=test-only-signing-key
                VNPAY_RETURN_URL=http://localhost:3000/billing/checkout/result
                """);

        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(TestConfiguration.class)
                .withPropertyValues(
                        "spring.profiles.active=sandbox",
                        "spring.config.location=classpath:application-sandbox.yml",
                        "spring.config.import=" + file.toUri() + "[.properties]")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var properties = context.getBean(BillingProperties.class);
                    assertThat(properties.isPaymentsEnabled()).isTrue();
                    assertThat(properties.getVnpay().getTmnCode()).isEqualTo("TESTTMN1");
                    assertThat(properties.getVnpay().getPaymentUrl()).isEqualTo(
                            "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
                    assertThat(context).hasBean("verifyVnpaySandboxEndpoints");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(BillingProperties.class)
    @Import(VnpaySandboxConfiguration.class)
    static class TestConfiguration {
    }
}
