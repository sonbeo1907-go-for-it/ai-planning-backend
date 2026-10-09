package com.codegym.aiplanning.service.billing.gateway;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class VnpaySecurityUtil {

    private VnpaySecurityUtil() {}

    public static String buildHashData(Map<String, String> fields) {
        List<String> fieldNames = fields.keySet().stream()
                .filter(name -> name != null && !name.isBlank())
                .filter(name -> !"vnp_SecureHash".equals(name) && !"vnp_SecureHashType".equals(name))
                .filter(name -> fields.get(name) != null && !fields.get(name).isBlank())
                .sorted()
                .toList();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fieldNames.size(); i++) {
            String name = fieldNames.get(i);
            String val = fields.get(name);
            sb.append(name).append('=').append(URLEncoder.encode(val, StandardCharsets.US_ASCII));
            if (i < fieldNames.size() - 1) {
                sb.append('&');
            }
        }
        return sb.toString();
    }

    public static String hashAllFields(Map<String, String> fields, String secretKey) {
        String data = buildHashData(fields);
        return hmacSHA512(secretKey, data);
    }

    public static boolean verifySignature(Map<String, String> fields, String secretKey) {
        String secureHash = fields.get("vnp_SecureHash");
        if (secureHash == null || secureHash.isBlank() || secretKey == null || secretKey.isBlank()) {
            return false;
        }
        String calculated = hashAllFields(fields, secretKey);
        return secureHash.equalsIgnoreCase(calculated);
    }

    public static String hmacSHA512(String key, String data) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac.init(secretKey);
            byte[] hash = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : hash) {
                result.append(String.format("%02x", b));
            }
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA512", e);
        }
    }
}
