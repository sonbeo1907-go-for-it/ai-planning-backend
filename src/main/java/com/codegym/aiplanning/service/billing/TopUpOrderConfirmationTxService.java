package com.codegym.aiplanning.service.billing;

import com.codegym.aiplanning.controller.billing.dto.VnpayIpnResponse;
import com.codegym.aiplanning.entity.billing.CreditLedgerEntry;
import com.codegym.aiplanning.entity.billing.LedgerEntryType;
import com.codegym.aiplanning.entity.billing.LedgerReferenceType;
import com.codegym.aiplanning.entity.billing.TopUpOrder;
import com.codegym.aiplanning.entity.billing.TopUpOrderStatus;
import com.codegym.aiplanning.repository.billing.CreditLedgerEntryRepository;
import com.codegym.aiplanning.repository.billing.CreditWalletRepository;
import com.codegym.aiplanning.repository.billing.TopUpOrderRepository;
import com.codegym.aiplanning.service.billing.gateway.VerifiedPayment;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TopUpOrderConfirmationTxService {
    private final TopUpOrderRepository orderRepository;
    private final CreditWalletRepository walletRepository;
    private final CreditLedgerEntryRepository ledgerRepository;
    private final CreditWalletAtomicInitializer walletInitializer;
    private final BillingAbnormalTransactionService abnormalService;

    public TopUpOrderConfirmationTxService(TopUpOrderRepository orderRepository,
            CreditWalletRepository walletRepository, CreditLedgerEntryRepository ledgerRepository,
            CreditWalletAtomicInitializer walletInitializer, BillingAbnormalTransactionService abnormalService) {
        this.orderRepository = orderRepository;
        this.walletRepository = walletRepository;
        this.ledgerRepository = ledgerRepository;
        this.walletInitializer = walletInitializer;
        this.abnormalService = abnormalService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public VnpayIpnResponse confirmPayment(VerifiedPayment payment) {
        var found = orderRepository.findByOrderCodeForUpdate(payment.orderCode());
        if (found.isEmpty()) return VnpayIpnResponse.orderNotFound();
        TopUpOrder order = found.get();
        if (!payment.provider().equals(order.getPaymentProvider()) || !"VND".equals(order.getCurrency())) {
            recordAbnormal(order, payment, "PROVIDER_OR_CURRENCY_MISMATCH");
            return VnpayIpnResponse.error("Payment could not be confirmed");
        }
        if (payment.amountMinor() != Math.multiplyExact(order.getPriceVndSnapshot(), 100L)) {
            recordAbnormal(order, payment, "AMOUNT_MISMATCH");
            return VnpayIpnResponse.invalidAmount();
        }
        if (!payment.queryVerified() && "00".equals(payment.responseCode()) != "00".equals(payment.transactionStatus())) {
            recordAbnormal(order, payment, "CONTRADICTORY_IPN_STATUS");
            return VnpayIpnResponse.error("Payment requires reconciliation");
        }
        if (order.getStatus() == TopUpOrderStatus.PAID) {
            if (payment.succeeded() && payment.transactionId().equals(order.getExternalTransactionId())) {
                return VnpayIpnResponse.orderAlreadyConfirmed();
            }
            recordAbnormal(order, payment, "ORDER_ALREADY_PAID_DIFFERENT_TRANSACTION");
            return VnpayIpnResponse.error("Payment requires reconciliation");
        }
        if ((order.getStatus() == TopUpOrderStatus.CANCELLED || order.getStatus() == TopUpOrderStatus.FAILED)
                && payment.succeeded() && !payment.queryVerified()) {
            recordAbnormal(order, payment, "LATE_PAYMENT_ON_" + order.getStatus() + "_ORDER");
            return VnpayIpnResponse.error("Payment requires reconciliation");
        }
        if (!payment.succeeded()) {
            if (order.getStatus() == TopUpOrderStatus.PENDING) {
                order.setStatus(!order.getExpiresAt().isAfter(Instant.now()) ? TopUpOrderStatus.EXPIRED
                        : "24".equals(payment.responseCode()) ? TopUpOrderStatus.CANCELLED : TopUpOrderStatus.FAILED);
                // Failed attempts do not claim a successful provider transaction identifier.
                orderRepository.saveAndFlush(order);
            }
            return VnpayIpnResponse.success();
        }
        if (payment.transactionId() == null || !payment.transactionId().matches("[0-9]{1,15}")
                || Long.parseLong(payment.transactionId()) == 0) {
            return VnpayIpnResponse.error("Missing successful transaction identifier");
        }
        var duplicate = orderRepository.findByExternalTransactionId(payment.transactionId());
        if (duplicate.isPresent() && !duplicate.get().getId().equals(order.getId())) {
            recordAbnormal(order, payment, "TRANSACTION_ALREADY_USED");
            return VnpayIpnResponse.error("Payment requires reconciliation");
        }
        // Expiry prevents NEW checkout, never discards signed evidence of money already paid.
        order.setStatus(TopUpOrderStatus.PAID);
        order.setExternalTransactionId(payment.transactionId());
        order.setPaidAt(Instant.now());
        orderRepository.saveAndFlush(order); // Unique transaction constraint also protects concurrent orders.

        var wallet = walletInitializer.getOrCreate(order.getUserId());
        long credits = order.getTotalCreditsSnapshot();
        long available = Math.addExact(wallet.getAvailableCredits(), credits);
        wallet.updateProjection(available, wallet.getReservedCredits());
        walletRepository.saveAndFlush(wallet);
        ledgerRepository.saveAndFlush(CreditLedgerEntry.create(
                wallet, wallet.getUser(), LedgerEntryType.TOP_UP, credits, 0L,
                available, wallet.getReservedCredits(), LedgerReferenceType.ORDER, order.getId(),
                "TOP_UP:" + order.getId(), "Top-up AI Credits: " + order.getPackageNameSnapshot()));
        return VnpayIpnResponse.success();
    }

    private void recordAbnormal(TopUpOrder order, VerifiedPayment payment, String reason) {
        // Store bounded normalized evidence only; no hash, bank account/card or raw callback payload.
        String evidence = "provider=" + payment.provider() + ";responseCode=" + payment.responseCode()
                + ";transactionStatus=" + payment.transactionStatus() + ";queryVerified=" + payment.queryVerified();
        abnormalService.recordAbnormal(order.getOrderCode(), payment.transactionId(), payment.amountMinor() / 100,
                reason, "Verified payment requires reconciliation", evidence);
    }
}
