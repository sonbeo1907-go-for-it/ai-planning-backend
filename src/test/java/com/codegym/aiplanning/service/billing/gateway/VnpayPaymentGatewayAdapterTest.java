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
        var properties = new com.codegym.aiplanning.config.BillingProperties();
        properties.setPaymentsEnabled(true);
        properties.getVnpay().setTmnCode(tmnCode);
        properties.getVnpay().setHashSecret(secretKey);
        adapter = new VnpayPaymentGatewayAdapter(properties, new com.fasterxml.jackson.databind.ObjectMapper());
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

        ReflectionTestUtils.setField(order, "createdAt", Instant.now());
        CheckoutResult result = adapter.createCheckout(new CheckoutRequest(order, "203.0.113.10"));

        assertThat(result.paymentProvider()).isEqualTo("VNPAY");
        assertThat(result.checkoutReference()).isEqualTo("TOPUP-123456");

        URI uri = URI.create(result.checkoutUrl());
        assertThat(uri.getScheme()).isEqualTo("https");
        assertThat(uri.getHost()).isEqualTo("sandbox.vnpayment.vn");

        String query = uri.getRawQuery();
        assertThat(query).contains("vnp_SecureHash=");
        assertThat(query).contains("vnp_CreateDate=");
        assertThat(query).contains("vnp_IpAddr=203.0.113.10");
        assertThat(query).contains("vnp_ExpireDate=");
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
        assertThat(adapter.createCheckout(new CheckoutRequest(order, "203.0.113.10")).checkoutUrl())
                .isEqualTo(result.checkoutUrl());
    }

    @Test
    @SuppressWarnings("unchecked")
    void queryVerifiesSignedResponseAndRejectsTampering() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var order = new TopUpOrder(UUID.randomUUID(), "TOPUP-123", null, "TEST", "Test credits",
                50_000, 5_000, 0, 5_000, TopUpOrderStatus.CANCELLED, "VND", "VNPAY", "TOPUP-123",
                "test-query", Instant.now().plusSeconds(600));
        ReflectionTestUtils.setField(order, "createdAt", Instant.parse("2026-10-10T10:00:00Z"));
        Map<String, String> fields = new HashMap<>();
        fields.put("vnp_ResponseId", "Q1");
        fields.put("vnp_Command", "querydr");
        fields.put("vnp_ResponseCode", "00");
        fields.put("vnp_Message", "Success");
        fields.put("vnp_TmnCode", tmnCode);
        fields.put("vnp_TxnRef", "TOPUP-123");
        fields.put("vnp_Amount", "5000000");
        fields.put("vnp_BankCode", "NCB");
        fields.put("vnp_PayDate", "20261010100000");
        fields.put("vnp_TransactionNo", "123456");
        fields.put("vnp_TransactionType", "01");
        fields.put("vnp_TransactionStatus", "00");
        fields.put("vnp_OrderInfo", "Topup credits");
        // Independent .NET HMACSHA512 fixture for VNPAY's pipe-delimited QueryDr format.
        fields.put("vnp_SecureHash", "4460b6f224e615b9af7d2059722a44ea56a508554e21fde593c6a5d20934d9950"
                + "5633b28d5ad880eb60369258f510fb4d13cc350297d3cea2e715f712aa51788");
        var client = org.mockito.Mockito.mock(java.net.http.HttpClient.class);
        var builder = org.mockito.Mockito.mock(java.net.http.HttpClient.Builder.class);
        java.net.http.HttpResponse<String> response = org.mockito.Mockito.mock(java.net.http.HttpResponse.class);
        org.mockito.Mockito.when(builder.connectTimeout(org.mockito.ArgumentMatchers.any())).thenReturn(builder);
        org.mockito.Mockito.when(builder.build()).thenReturn(client);
        org.mockito.Mockito.when(response.statusCode()).thenReturn(200);
        org.mockito.Mockito.when(response.body()).thenAnswer(invocation -> mapper.writeValueAsString(fields));
        org.mockito.Mockito.when(client.send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn((java.net.http.HttpResponse) response);
        try (var http = org.mockito.Mockito.mockStatic(java.net.http.HttpClient.class)) {
            http.when(java.net.http.HttpClient::newBuilder).thenReturn(builder);
            var result = adapter.queryPayment(order);
            assertThat(result.queryVerified()).isTrue();
            assertThat(result.succeeded()).isTrue();
            assertThat(result.transactionId()).isEqualTo("123456");
            fields.put("vnp_Amount", "4000000");
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter.queryPayment(order))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void verifiesIndependentCallbackFixtureWithoutCurrency() {
        // Signature calculated independently using .NET HMACSHA512 over a fixed canonical string.
        Map<String, String> fields = new HashMap<>(Map.of(
                "vnp_Amount", "5000000", "vnp_BankCode", "NCB", "vnp_OrderInfo", "Topup credits",
                "vnp_ResponseCode", "00", "vnp_TmnCode", tmnCode, "vnp_TransactionNo", "123456",
                "vnp_TransactionStatus", "00", "vnp_TxnRef", "TOPUP-123"));
        fields.put("vnp_SecureHash", "a44adb74bed9698e8ae739fa8c2809af103c2223d16c55ea4f705c304c53d5f2d"
                + "b21bb2381037b4975d66c13a634dc3039a8a01cda7308a2365df3d2c39ed6c2");
        VerifiedPayment payment = adapter.verifyNotification(fields);
        assertThat(payment.succeeded()).isTrue();
        assertThat(payment.amountMinor()).isEqualTo(5_000_000);
        fields.put("vnp_Amount", "4000000");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter.verifyNotification(fields))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
