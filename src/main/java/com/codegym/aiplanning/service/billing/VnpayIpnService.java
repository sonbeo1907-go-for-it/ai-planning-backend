package com.codegym.aiplanning.service.billing;

import com.codegym.aiplanning.config.BillingProperties;
import com.codegym.aiplanning.controller.billing.dto.VnpayIpnResponse;
import com.codegym.aiplanning.service.billing.gateway.VnpaySecurityUtil;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class VnpayIpnService {

    private static final Logger log = LoggerFactory.getLogger(VnpayIpnService.class);

    private final BillingProperties billingProperties;
    private final TopUpOrderConfirmationTxService confirmationTxService;

    public VnpayIpnService(
            BillingProperties billingProperties,
            TopUpOrderConfirmationTxService confirmationTxService) {
        this.billingProperties = billingProperties;
        this.confirmationTxService = confirmationTxService;
    }

    public VnpayIpnResponse processIpn(Map<String, String> params) {
        log.info("Processing VNPAY IPN webhook with {} params: {}", params != null ? params.size() : 0, params);

        if (params == null || params.isEmpty()) {
            log.warn("IPN rejected: empty parameters");
            return VnpayIpnResponse.invalidChecksum();
        }

        String hashSecret = billingProperties.getVnpay().getHashSecret();
        String expectedTmnCode = billingProperties.getVnpay().getTmnCode();

        // 1. Verify signature
        boolean isValidSignature = VnpaySecurityUtil.verifySignature(params, hashSecret);
        if (!isValidSignature) {
            log.warn("IPN rejected: invalid checksum signature");
            return VnpayIpnResponse.invalidChecksum();
        }

        // 2. Verify TmnCode
        String receivedTmnCode = params.get("vnp_TmnCode");
        if (receivedTmnCode == null || !receivedTmnCode.equals(expectedTmnCode)) {
            log.warn("IPN rejected: invalid TmnCode. Expected={}, Received={}", expectedTmnCode, receivedTmnCode);
            return VnpayIpnResponse.invalidChecksum();
        }

        // 3. Extract order reference and amount
        String orderCode = params.get("vnp_TxnRef");
        if (orderCode == null || orderCode.isBlank()) {
            log.warn("IPN rejected: missing vnp_TxnRef");
            return VnpayIpnResponse.orderNotFound();
        }

        String amountStr = params.get("vnp_Amount");
        long vnpAmount;
        try {
            vnpAmount = Long.parseLong(amountStr);
        } catch (Exception ex) {
            log.warn("IPN rejected: invalid amount string: {}", amountStr);
            return VnpayIpnResponse.invalidAmount();
        }

        String vnpTxnNo = params.get("vnp_TransactionNo");
        String vnpResponseCode = params.get("vnp_ResponseCode");
        String vnpTransactionStatus = params.get("vnp_TransactionStatus");

        // 4. Delegate to transactional confirmation service
        try {
            return confirmationTxService.confirmPayment(
                    orderCode.trim(),
                    vnpTxnNo != null ? vnpTxnNo.trim() : null,
                    vnpAmount,
                    vnpResponseCode != null ? vnpResponseCode.trim() : "",
                    vnpTransactionStatus != null ? vnpTransactionStatus.trim() : "",
                    params);
        } catch (Exception ex) {
            log.error("Error occurred while processing IPN for orderCode={}", orderCode, ex);
            return VnpayIpnResponse.error("Database or system error: " + ex.getMessage());
        }
    }
}
