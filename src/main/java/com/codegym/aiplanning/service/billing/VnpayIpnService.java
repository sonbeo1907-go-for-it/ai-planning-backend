package com.codegym.aiplanning.service.billing;

import com.codegym.aiplanning.controller.billing.dto.VnpayIpnResponse;
import com.codegym.aiplanning.entity.billing.TopUpOrderStatus;
import com.codegym.aiplanning.repository.billing.TopUpOrderRepository;
import com.codegym.aiplanning.service.billing.gateway.PaymentGateway;
import com.codegym.aiplanning.service.billing.gateway.VerifiedPayment;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class VnpayIpnService {
    private static final Logger log = LoggerFactory.getLogger(VnpayIpnService.class);
    private final PaymentGateway gateway;
    private final TopUpOrderRepository orderRepository;
    private final TopUpOrderConfirmationTxService confirmationService;

    public VnpayIpnService(PaymentGateway gateway, TopUpOrderRepository orderRepository,
            TopUpOrderConfirmationTxService confirmationService) {
        this.gateway = gateway;
        this.orderRepository = orderRepository;
        this.confirmationService = confirmationService;
    }

    public VnpayIpnResponse processIpn(Map<String, String> params) {
        try {
            VerifiedPayment payment = gateway.verifyNotification(params);
            var order = orderRepository.findByOrderCode(payment.orderCode());
            if (order.isEmpty()) return VnpayIpnResponse.orderNotFound();
            boolean conflict = payment.succeeded()
                    && (order.get().getStatus() == TopUpOrderStatus.CANCELLED
                            || order.get().getStatus() == TopUpOrderStatus.FAILED);
            if (conflict) {
                // Network access happens before confirmation acquires database locks.
                try {
                    VerifiedPayment queried = gateway.queryPayment(order.get());
                    if (queried != null && queried.queryVerified() && queried.succeeded()
                            && payment.provider().equals(queried.provider())
                            && payment.orderCode().equals(queried.orderCode())
                            && payment.transactionId().equals(queried.transactionId())
                            && payment.amountMinor() == queried.amountMinor()) {
                        payment = queried;
                    }
                } catch (RuntimeException exception) {
                    log.warn("Payment query unavailable; signed conflict retained for reconciliation");
                }
            }
            return confirmationService.confirmPayment(payment);
        } catch (IllegalArgumentException exception) {
            // Invalid signatures/fields never reach the financial transaction boundary.
            return VnpayIpnResponse.invalidChecksum();
        } catch (RuntimeException exception) {
            // Do not expose database, signature, provider response, or customer details.
            log.error("Payment notification processing failed: exceptionType={}", exception.getClass().getSimpleName());
            return VnpayIpnResponse.error("Payment confirmation temporarily unavailable");
        }
    }
}
