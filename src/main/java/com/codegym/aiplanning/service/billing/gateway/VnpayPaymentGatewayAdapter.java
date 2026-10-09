package com.codegym.aiplanning.service.billing.gateway;

import com.codegym.aiplanning.entity.billing.TopUpOrder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class VnpayPaymentGatewayAdapter implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(VnpayPaymentGatewayAdapter.class);
    private static final String PROVIDER_NAME = "VNPAY";

    @Value("${app.billing.vnpay.payment-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
    private String paymentUrl;

    @Value("${app.billing.vnpay.tmn-code:VNPAYTMN}")
    private String tmnCode;

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
            String orderInfo = URLEncoder.encode("Top-up AI Credits: " + order.getPackageNameSnapshot(), StandardCharsets.UTF_8);
            String encodedReturnUrl = URLEncoder.encode(returnUrl, StandardCharsets.UTF_8);

            String redirectUrl = String.format(
                    "%s?vnp_Version=2.1.0&vnp_Command=pay&vnp_TmnCode=%s&vnp_Amount=%d&vnp_CurrCode=VND&vnp_TxnRef=%s&vnp_OrderInfo=%s&vnp_ReturnUrl=%s",
                    paymentUrl,
                    tmnCode,
                    vnpAmount,
                    order.getOrderCode(),
                    orderInfo,
                    encodedReturnUrl);

            return new CheckoutResult(redirectUrl, checkoutRef, PROVIDER_NAME);
        } catch (Exception ex) {
            log.error("Failed to generate VNPAY checkout URL for order: {}", order.getOrderCode(), ex);
            throw ex;
        }
    }
}
