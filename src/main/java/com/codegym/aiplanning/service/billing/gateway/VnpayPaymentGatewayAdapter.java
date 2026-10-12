package com.codegym.aiplanning.service.billing.gateway;

import com.codegym.aiplanning.config.BillingProperties;
import com.codegym.aiplanning.entity.billing.TopUpOrder;
import com.codegym.aiplanning.entity.billing.TopUpOrderStatus;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class VnpayPaymentGatewayAdapter implements PaymentGateway {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private final BillingProperties properties;
    private final ObjectMapper objectMapper;

    public VnpayPaymentGatewayAdapter(BillingProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderName() {
        return "VNPAY";
    }

    @Override
    public CheckoutResult createCheckout(CheckoutRequest request) {
        requireEnabled();
        TopUpOrder order = request.order();
        if (!"VND".equals(order.getCurrency()) || !"VNPAY".equals(order.getPaymentProvider())
                || order.getStatus() != TopUpOrderStatus.PENDING
                || !order.getExpiresAt().isAfter(Instant.now())
                || request.clientIp() == null || request.clientIp().isBlank()) {
            throw new IllegalArgumentException("Order is not eligible for checkout");
        }
        var config = properties.getVnpay();
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", config.getTmnCode());
        params.put("vnp_Amount", String.valueOf(Math.multiplyExact(order.getPriceVndSnapshot(), 100L)));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", order.getOrderCode());
        params.put("vnp_OrderInfo", "Nap AI Credits: " + order.getOrderCode());
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", config.getReturnUrl());
        params.put("vnp_IpAddr", request.clientIp());
        // Stable across repeated checkout attempts; QueryDr uses this merchant transaction date.
        params.put("vnp_CreateDate", DATE_FORMATTER.format(order.getCreatedAt()));
        params.put("vnp_ExpireDate", DATE_FORMATTER.format(order.getExpiresAt()));
        String url = config.getPaymentUrl() + "?" + VnpaySecurityUtil.buildHashData(params)
                + "&vnp_SecureHash=" + VnpaySecurityUtil.hashAllFields(params, config.getHashSecret());
        return new CheckoutResult(url, order.getOrderCode(), getProviderName());
    }

    @Override
    public VerifiedPayment verifyNotification(Map<String, String> params) {
        requireCredentials();
        if (params == null || params.size() > 32 || params.entrySet().stream()
                .anyMatch(entry -> entry.getKey() == null || entry.getValue() == null
                        || entry.getKey().length() > 64 || entry.getValue().length() > 1024)
                || !VnpaySecurityUtil.verifySignature(params, properties.getVnpay().getHashSecret())
                || !properties.getVnpay().getTmnCode().equals(params.get("vnp_TmnCode"))) {
            throw new IllegalArgumentException("Invalid payment signature");
        }
        return normalize(params, false);
    }

    private VerifiedPayment normalize(Map<String, String> params, boolean queryVerified) {
        String orderCode = params.get("vnp_TxnRef");
        String amount = params.get("vnp_Amount");
        String transaction = params.get("vnp_TransactionNo");
        String response = params.get("vnp_ResponseCode");
        String status = params.get("vnp_TransactionStatus");
        if (orderCode == null || !orderCode.matches("[A-Za-z0-9_-]{1,64}")
                || amount == null || !amount.matches("[0-9]{1,12}")
                || response == null || !response.matches("[0-9]{2}")
                || status == null || !status.matches("[0-9]{2}")
                || transaction == null || !transaction.matches("[0-9]{1,15}")
                || ("00".equals(status) && Long.parseLong(transaction) == 0)
                || params.get("vnp_BankCode") == null || params.get("vnp_BankCode").isBlank()
                || params.get("vnp_OrderInfo") == null || params.get("vnp_OrderInfo").isBlank()) {
            throw new IllegalArgumentException("Invalid payment fields");
        }
        return new VerifiedPayment(getProviderName(), orderCode, transaction,
                Long.parseLong(amount), response, status, queryVerified);
    }

    @Override
    public VerifiedPayment queryPayment(TopUpOrder order) {
        requireCredentials();
        var config = properties.getVnpay();
        Map<String, String> request = new HashMap<>();
        request.put("vnp_RequestId", UUID.randomUUID().toString().replace("-", ""));
        request.put("vnp_Version", "2.1.0");
        request.put("vnp_Command", "querydr");
        request.put("vnp_TmnCode", config.getTmnCode());
        request.put("vnp_TxnRef", order.getOrderCode());
        request.put("vnp_TransactionDate", DATE_FORMATTER.format(order.getCreatedAt()));
        request.put("vnp_CreateDate", DATE_FORMATTER.format(Instant.now()));
        request.put("vnp_IpAddr", config.getServerIp());
        request.put("vnp_OrderInfo", "Query payment " + order.getOrderCode());
        request.put("vnp_SecureHash", signPipeFields(request, "vnp_RequestId", "vnp_Version", "vnp_Command",
                "vnp_TmnCode", "vnp_TxnRef", "vnp_TransactionDate", "vnp_CreateDate", "vnp_IpAddr", "vnp_OrderInfo"));
        try {
            Duration timeout = Duration.ofSeconds(config.getTimeoutSeconds());
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(config.getQueryUrl()))
                    .timeout(timeout).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request))).build();
            HttpResponse<String> response = HttpClient.newBuilder().connectTimeout(timeout).build()
                    .send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body().length() > 16_384) {
                throw new IllegalStateException("Payment query unavailable");
            }
            Map<String, String> result = objectMapper.readValue(response.body(), new TypeReference<>() {});
            String expectedHash = signPipeFields(result, "vnp_ResponseId", "vnp_Command", "vnp_ResponseCode",
                    "vnp_Message", "vnp_TmnCode", "vnp_TxnRef", "vnp_Amount", "vnp_BankCode", "vnp_PayDate",
                    "vnp_TransactionNo", "vnp_TransactionType", "vnp_TransactionStatus", "vnp_OrderInfo",
                    "vnp_PromotionCode", "vnp_PromotionAmount");
            if (!VnpaySecurityUtil.matchesHash(result.get("vnp_SecureHash"), expectedHash)
                    || !config.getTmnCode().equals(result.get("vnp_TmnCode"))
                    || !order.getOrderCode().equals(result.get("vnp_TxnRef"))
                    || !"00".equals(result.get("vnp_ResponseCode"))
                    || !"01".equals(result.get("vnp_TransactionType"))) {
                throw new IllegalStateException("Payment query could not be verified");
            }
            return normalize(result, true);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Payment query interrupted");
        } catch (Exception exception) {
            throw new IllegalStateException("Payment query unavailable");
        }
    }

    private String signPipeFields(Map<String, String> fields, String... names) {
        String data = java.util.Arrays.stream(names).map(name -> fields.getOrDefault(name, ""))
                .collect(java.util.stream.Collectors.joining("|"));
        return VnpaySecurityUtil.hmacSHA512(properties.getVnpay().getHashSecret(), data);
    }

    private void requireEnabled() {
        if (!properties.isPaymentsEnabled() || !properties.isPaymentConfigurationValid()) {
            throw new IllegalStateException("Payments are unavailable");
        }
    }

    private void requireCredentials() {
        // Disabling new purchases must not block settlement of existing paid orders.
        if (!properties.isGatewayConfigurationValid()) {
            throw new IllegalStateException("Payment verification is unavailable");
        }
    }
}
