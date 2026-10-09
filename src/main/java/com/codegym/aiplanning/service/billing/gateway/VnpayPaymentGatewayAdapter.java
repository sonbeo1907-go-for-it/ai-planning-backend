package com.codegym.aiplanning.service.billing.gateway;

import com.codegym.aiplanning.entity.billing.TopUpOrder;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class VnpayPaymentGatewayAdapter implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(VnpayPaymentGatewayAdapter.class);
    private static final String PROVIDER_NAME = "VNPAY";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Value("${app.billing.vnpay.payment-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
    private String paymentUrl;

    @Value("${app.billing.vnpay.tmn-code:VNPAYTMN}")
    private String tmnCode;

    @Value("${app.billing.vnpay.hash-secret:SECRETKEY123}")
    private String hashSecret;

    @Value("${app.billing.vnpay.return-url:http://localhost:3000/billing/checkout/result}")
    private String returnUrl;

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public CheckoutResult createCheckout(TopUpOrder order) {
        log.info("Creating VNPAY checkout for order: {}", order.getOrderCode());
        try {
            String checkoutRef = "VNP_" + order.getOrderCode();
            long vnpAmount = order.getPriceVndSnapshot() * 100L; // VNPAY multiplies VND by 100
            String createDate = DATE_FORMATTER.format(ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")));

            Map<String, String> vnpParams = new HashMap<>();
            vnpParams.put("vnp_Version", "2.1.0");
            vnpParams.put("vnp_Command", "pay");
            vnpParams.put("vnp_TmnCode", tmnCode);
            vnpParams.put("vnp_Amount", String.valueOf(vnpAmount));
            vnpParams.put("vnp_CurrCode", "VND");
            vnpParams.put("vnp_TxnRef", order.getOrderCode());
            vnpParams.put("vnp_OrderInfo", "Nap AI Credits: " + order.getOrderCode());
            vnpParams.put("vnp_OrderType", "other");
            vnpParams.put("vnp_Locale", "vn");
            vnpParams.put("vnp_ReturnUrl", returnUrl);
            vnpParams.put("vnp_IpAddr", "127.0.0.1");
            vnpParams.put("vnp_CreateDate", createDate);

            String queryUrl = VnpaySecurityUtil.buildHashData(vnpParams);
            String secureHash = VnpaySecurityUtil.hashAllFields(vnpParams, hashSecret);
            String redirectUrl = paymentUrl + "?" + queryUrl + "&vnp_SecureHash=" + secureHash;

            return new CheckoutResult(redirectUrl, checkoutRef, PROVIDER_NAME);
        } catch (Exception ex) {
            log.error("Failed to generate VNPAY checkout URL for order: {}", order.getOrderCode(), ex);
            throw ex;
        }
    }
}
