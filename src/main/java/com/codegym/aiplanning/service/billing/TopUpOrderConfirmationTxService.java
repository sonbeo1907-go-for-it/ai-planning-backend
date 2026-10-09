package com.codegym.aiplanning.service.billing;

import com.codegym.aiplanning.controller.billing.dto.VnpayIpnResponse;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.billing.CreditLedgerEntry;
import com.codegym.aiplanning.entity.billing.CreditWallet;
import com.codegym.aiplanning.entity.billing.LedgerEntryType;
import com.codegym.aiplanning.entity.billing.LedgerReferenceType;
import com.codegym.aiplanning.entity.billing.TopUpOrder;
import com.codegym.aiplanning.entity.billing.TopUpOrderStatus;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.repository.billing.CreditLedgerEntryRepository;
import com.codegym.aiplanning.repository.billing.CreditWalletRepository;
import com.codegym.aiplanning.repository.billing.TopUpOrderRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TopUpOrderConfirmationTxService {

    private static final Logger log = LoggerFactory.getLogger(TopUpOrderConfirmationTxService.class);

    private final TopUpOrderRepository topUpOrderRepository;
    private final CreditWalletRepository creditWalletRepository;
    private final CreditLedgerEntryRepository creditLedgerEntryRepository;
    private final UserAccountRepository userAccountRepository;
    private final BillingAbnormalTransactionService abnormalTransactionService;

    public TopUpOrderConfirmationTxService(
            TopUpOrderRepository topUpOrderRepository,
            CreditWalletRepository creditWalletRepository,
            CreditLedgerEntryRepository creditLedgerEntryRepository,
            UserAccountRepository userAccountRepository,
            BillingAbnormalTransactionService abnormalTransactionService) {
        this.topUpOrderRepository = topUpOrderRepository;
        this.creditWalletRepository = creditWalletRepository;
        this.creditLedgerEntryRepository = creditLedgerEntryRepository;
        this.userAccountRepository = userAccountRepository;
        this.abnormalTransactionService = abnormalTransactionService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public VnpayIpnResponse confirmPayment(
            String orderCode,
            String vnpTxnNo,
            long vnpAmount,
            String vnpResponseCode,
            String vnpTransactionStatus,
            Map<String, String> rawParams) {

        String rawPayload = rawParams != null ? rawParams.toString() : "";
        String normalizedTxnNo = (vnpTxnNo != null && !vnpTxnNo.isBlank() && !"0".equals(vnpTxnNo.trim()))
                ? vnpTxnNo.trim()
                : null;

        // 1. Lock and find order by orderCode
        Optional<TopUpOrder> orderOpt = topUpOrderRepository.findByOrderCodeForUpdate(orderCode);
        if (orderOpt.isEmpty()) {
            log.warn("IPN rejected: orderCode {} not found", orderCode);
            return VnpayIpnResponse.orderNotFound();
        }

        TopUpOrder order = orderOpt.get();

        // 2. Amount verification: VNPAY amount is VND * 100
        long expectedVnpAmount = order.getPriceVndSnapshot() * 100L;
        if (vnpAmount != expectedVnpAmount) {
            log.warn("IPN rejected: invalid amount for order {}. Expected={}, Received={}",
                    orderCode, expectedVnpAmount, vnpAmount);
            abnormalTransactionService.recordAbnormal(
                    orderCode,
                    vnpTxnNo,
                    vnpAmount / 100L,
                    "AMOUNT_MISMATCH",
                    String.format("Expected %d but received %d", expectedVnpAmount, vnpAmount),
                    rawPayload);
            return VnpayIpnResponse.invalidAmount();
        }

        // 3. Status consistency verification
        boolean isSuccess = "00".equals(vnpResponseCode) && "00".equals(vnpTransactionStatus);
        boolean isContradictory = ("00".equals(vnpResponseCode) && !"00".equals(vnpTransactionStatus))
                || (!"00".equals(vnpResponseCode) && "00".equals(vnpTransactionStatus));

        if (isContradictory) {
            log.warn("IPN rejected: contradictory status for order {}. ResponseCode={}, TransactionStatus={}",
                    orderCode, vnpResponseCode, vnpTransactionStatus);
            abnormalTransactionService.recordAbnormal(
                    orderCode,
                    vnpTxnNo,
                    order.getPriceVndSnapshot(),
                    "CONTRADICTORY_IPN_STATUS",
                    String.format("ResponseCode=%s, TransactionStatus=%s", vnpResponseCode, vnpTransactionStatus),
                    rawPayload);
            return VnpayIpnResponse.error("Contradictory response code and transaction status");
        }

        // 4. State transition handling
        TopUpOrderStatus currentStatus = order.getStatus();

        // Case 4A: Order is already PAID
        if (currentStatus == TopUpOrderStatus.PAID) {
            if (order.getExternalTransactionId() != null && order.getExternalTransactionId().equals(vnpTxnNo)) {
                log.info("Duplicate IPN for already confirmed order: {}, txn: {}", orderCode, vnpTxnNo);
                return VnpayIpnResponse.orderAlreadyConfirmed();
            } else {
                log.warn("Order {} already PAID but callback contains different transaction ID: {} vs existing {}",
                        orderCode, vnpTxnNo, order.getExternalTransactionId());
                abnormalTransactionService.recordAbnormal(
                        orderCode,
                        vnpTxnNo,
                        order.getPriceVndSnapshot(),
                        "ORDER_ALREADY_PAID_DIFFERENT_TRANSACTION",
                        String.format("Existing extTx=%s, incoming extTx=%s", order.getExternalTransactionId(), vnpTxnNo),
                        rawPayload);
                return VnpayIpnResponse.error("Order already confirmed with different transaction");
            }
        }

        // Case 4B: Order is in terminal state EXPIRED, CANCELLED, or FAILED
        if (currentStatus == TopUpOrderStatus.EXPIRED
                || currentStatus == TopUpOrderStatus.CANCELLED
                || currentStatus == TopUpOrderStatus.FAILED) {
            if (isSuccess) {
                log.warn("Late success payment received for order {} in terminal status {}", orderCode, currentStatus);
                abnormalTransactionService.recordAbnormal(
                        orderCode,
                        vnpTxnNo,
                        order.getPriceVndSnapshot(),
                        "LATE_PAYMENT_ON_" + currentStatus + "_ORDER",
                        "Success callback received while order is " + currentStatus,
                        rawPayload);
                return VnpayIpnResponse.error("Order is already " + currentStatus);
            } else {
                // Failure callback for already terminal order: confirm processed
                return VnpayIpnResponse.success();
            }
        }

        // Case 4C: Order is PENDING
        if (order.getExpiresAt().isBefore(Instant.now())) {
            order.setStatus(TopUpOrderStatus.EXPIRED);
            topUpOrderRepository.saveAndFlush(order);

            if (isSuccess) {
                log.warn("Order {} expired at {} but received successful IPN", orderCode, order.getExpiresAt());
                abnormalTransactionService.recordAbnormal(
                        orderCode,
                        vnpTxnNo,
                        order.getPriceVndSnapshot(),
                        "PAYMENT_RECEIVED_AFTER_EXPIRY",
                        "Order expired at " + order.getExpiresAt(),
                        rawPayload);
                return VnpayIpnResponse.error("Order has expired");
            } else {
                return VnpayIpnResponse.success();
            }
        }

        // Normal PENDING order processing
        if (isSuccess) {
            // Update Order
            order.setStatus(TopUpOrderStatus.PAID);
            order.setExternalTransactionId(vnpTxnNo);
            order.setPaidAt(Instant.now());
            topUpOrderRepository.saveAndFlush(order);

            // Lock and update Wallet
            UserAccount user = userAccountRepository.findById(order.getUserId())
                    .orElseThrow(() -> new IllegalStateException("User account not found: " + order.getUserId()));

            CreditWallet wallet = creditWalletRepository.findByUserIdForUpdate(order.getUserId())
                    .orElseGet(() -> {
                        CreditWallet newWallet = CreditWallet.create(user);
                        return creditWalletRepository.saveAndFlush(newWallet);
                    });

            long currentAvailable = wallet.getAvailableCredits();
            long delta = order.getTotalCreditsSnapshot();
            long newAvailable = currentAvailable + delta;

            wallet.updateProjection(newAvailable, wallet.getReservedCredits());
            creditWalletRepository.saveAndFlush(wallet);

            // Record CreditLedgerEntry
            CreditLedgerEntry entry = CreditLedgerEntry.create(
                    wallet,
                    user,
                    LedgerEntryType.TOP_UP,
                    delta,
                    0L,
                    newAvailable,
                    wallet.getReservedCredits(),
                    LedgerReferenceType.ORDER,
                    order.getId(),
                    "TOP_UP:" + order.getId(),
                    "Top-up AI Credits: " + order.getPackageNameSnapshot());
            creditLedgerEntryRepository.saveAndFlush(entry);

            log.info("Successfully confirmed payment for order: {}, credits added: {}", orderCode, delta);
            return VnpayIpnResponse.success();
        } else {
            // Unsuccessful payment on VNPAY
            TopUpOrderStatus failureStatus = "24".equals(vnpResponseCode)
                    ? TopUpOrderStatus.CANCELLED
                    : TopUpOrderStatus.FAILED;
            order.setStatus(failureStatus);
            order.setExternalTransactionId(normalizedTxnNo);
            topUpOrderRepository.saveAndFlush(order);

            log.info("Order {} marked as {} following VNPAY callback with responseCode {}",
                    orderCode, failureStatus, vnpResponseCode);
            return VnpayIpnResponse.success();
        }
    }
}
