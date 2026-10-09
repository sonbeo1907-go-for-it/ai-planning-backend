package com.codegym.aiplanning.service.billing.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.codegym.aiplanning.entity.billing.TopUpOrder;
import com.codegym.aiplanning.entity.billing.TopUpOrderStatus;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class VnpayPaymentGatewayAdapterTest {

    private VnpayPaymentGatewayAdapter adapter;
    private final String secretKey = "TEST_SECRET_KEY";
    private final String tmnCode = "TEST_TMN";

    @BeforeEach
    void setUp() {
        adapter = new VnpayPaymentGatewayAdapter();
        ReflectionTestUtils.setField(adapter, "paymentUrl", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        ReflectionTestUtils.setField(adapter, "tmnCode", tmnCode);
        ReflectionTestUtils.setField(adapter, "hashSecret", secretKey);
        ReflectionTestUtils.setField(adapter, "returnUrl", "http://localhost:3000/billing/checkout/result");
    }

    @Test
    void createCheckout_generatesStandardVnpayUrlWithSecureHashAndRequiredFields() {
        TopUpOrder order = new TopUpOrder(
                UUID.randomUUID(),
                "TOPUP-123456",
                UUID.randomUUID(),
                "AI_STARTER_50K",
                "Gói Khởi Đầu",
                50000L,
                5000L,
                0L,
                5000L,
                TopUpOrderStatus.PENDING,
                "VND",
                "VNPAY",
                null,
                "IDEMP-KEY-123",
                Instant.now().plus(15, ChronoUnit.MINUTES)
        );

        CheckoutResult result = adapter.createCheckout(order);

        assertThat(result.paymentProvider()).isEqualTo("VNPAY");
        assertThat(result.checkoutReference()).isEqualTo("VNP_TOPUP-123456");

        URI uri = URI.create(result.checkoutUrl());
        assertThat(uri.getScheme()).isEqualTo("https");
        assertThat(uri.getHost()).isEqualTo("sandbox.vnpayment.vn");

        String query = uri.getRawQuery();
        assertThat(query).contains("vnp_SecureHash=");
        assertThat(query).contains("vnp_CreateDate=");
        assertThat(query).contains("vnp_IpAddr=127.0.0.1");
        assertThat(query).contains("vnp_Locale=vn");
        assertThat(query).contains("vnp_TmnCode=" + tmnCode);
        assertThat(query).contains("vnp_Amount=5000000"); // 50000 * 100

        // Parse query params and verify signature
        Map<String, String> queryParams = new HashMap<>();
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            queryParams.put(parts[0], URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }

        assertThat(VnpaySecurityUtil.verifySignature(queryParams, secretKey)).isTrue();
    }
}
