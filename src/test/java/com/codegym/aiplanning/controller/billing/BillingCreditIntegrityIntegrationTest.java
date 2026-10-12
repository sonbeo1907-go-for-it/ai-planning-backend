package com.codegym.aiplanning.controller.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codegym.aiplanning.config.BillingProperties;
import com.codegym.aiplanning.entity.auth.*;
import com.codegym.aiplanning.entity.billing.*;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.repository.billing.*;
import com.codegym.aiplanning.service.billing.*;
import com.codegym.aiplanning.service.billing.gateway.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.data.domain.PageRequest;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BillingCreditIntegrityIntegrationTest {
    @Autowired private UserAccountRepository users;
    @Autowired private TopUpOrderRepository orders;
    @Autowired private CreditWalletRepository wallets;
    @SpyBean private CreditLedgerEntryRepository ledger;
    @SpyBean private PaymentGateway gateway;
    @Autowired private VnpayIpnService notifications;
    @Autowired private CreditWalletService walletService;
    @Autowired private BillingOrderService orderService;
    @Autowired private BillingProperties properties;
    @Autowired private MockMvc mvc;

    private UserAccount user() {
        return users.saveAndFlush(UserAccount.create(UUID.randomUUID() + "@example.com", "hash",
                UserRole.USER, AccountStatus.ACTIVE));
    }

    private TopUpOrder order(UserAccount owner, TopUpOrderStatus status, Instant expiry) {
        String code = "TOPUP-" + UUID.randomUUID();
        return orders.saveAndFlush(new TopUpOrder(owner.getId(), code, null, "TEST", "Test credits",
                50_000, 5_000, 0, 5_000, status, "VND", "VNPAY", code, UUID.randomUUID().toString(), expiry));
    }

    private Map<String, String> payment(TopUpOrder order) {
        var fields = new java.util.HashMap<String, String>();
        fields.put("vnp_TmnCode", properties.getVnpay().getTmnCode());
        fields.put("vnp_Amount", "5000000");
        fields.put("vnp_TxnRef", order.getOrderCode());
        fields.put("vnp_TransactionNo", String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits() % 999999999L) + 1));
        fields.put("vnp_ResponseCode", "00");
        fields.put("vnp_TransactionStatus", "00");
        fields.put("vnp_OrderInfo", "Topup credits");
        fields.put("vnp_BankCode", "NCB");
        sign(fields);
        return fields;
    }

    private void sign(Map<String, String> fields) {
        fields.put("vnp_SecureHash", VnpaySecurityUtil.hashAllFields(fields, properties.getVnpay().getHashSecret()));
    }

    @Test
    void expiredOrderAcceptsSignedLateSuccessExactlyOnceAndGrantsWelcomeBonus() {
        var owner = user();
        var order = order(owner, TopUpOrderStatus.EXPIRED, Instant.now().minusSeconds(60));
        var fields = payment(order); // The documented callback has no currency field.
        assertThat(notifications.processIpn(fields).rspCode()).isEqualTo("00");
        assertThat(notifications.processIpn(fields).rspCode()).isEqualTo("02");
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(TopUpOrderStatus.PAID);
        assertThat(walletService.getOrCreateWallet(owner.getId()).availableCredits()).isEqualTo(6000);
        var entries = ledger.findByUserIdOrderByRecordedAtDesc(owner.getId());
        assertThat(entries).filteredOn(entry -> entry.getEntryType() == LedgerEntryType.TOP_UP).hasSize(1);
        assertThat(entries).filteredOn(entry -> entry.getEntryType() == LedgerEntryType.WELCOME_BONUS).hasSize(1);
    }

    @Test
    void elapsedPendingCheckoutStillAcceptsPaidEvidence() {
        var order = order(user(), TopUpOrderStatus.PENDING, Instant.now().minusSeconds(60));
        assertThat(notifications.processIpn(payment(order)).rspCode()).isEqualTo("00");
    }

    @Test
    void cancelledOrderRequiresMatchingVerifiedQuery() {
        var order = order(user(), TopUpOrderStatus.CANCELLED, Instant.now().plusSeconds(600));
        var fields = payment(order);
        Mockito.doReturn(new VerifiedPayment("VNPAY", order.getOrderCode(), fields.get("vnp_TransactionNo"),
                5_000_000, "00", "00", true)).when(gateway).queryPayment(any());
        assertThat(notifications.processIpn(fields).rspCode()).isEqualTo("00");
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(TopUpOrderStatus.PAID);
    }

    @Test
    void malformedSuccessCannotCreditWallet() {
        var owner = user();
        var order = order(owner, TopUpOrderStatus.PENDING, Instant.now().plusSeconds(600));
        var fields = payment(order);
        fields.remove("vnp_TransactionNo");
        sign(fields);
        assertThat(notifications.processIpn(fields).rspCode()).isNotEqualTo("00");
        assertThat(wallets.findByUserId(owner.getId())).isEmpty();
        fields.put("vnp_TransactionNo", "0");
        sign(fields);
        assertThat(notifications.processIpn(fields).rspCode()).isNotEqualTo("00");
        assertThat(wallets.findByUserId(owner.getId())).isEmpty();
    }

    @Test
    void ledgerFailureRollsBackOrderWelcomeBonusAndTopUp() {
        var owner = user();
        var order = order(owner, TopUpOrderStatus.PENDING, Instant.now().plusSeconds(600));
        Mockito.doThrow(new IllegalStateException("secret internal database detail"))
                .when(ledger).saveAndFlush(any(CreditLedgerEntry.class));
        var response = notifications.processIpn(payment(order));
        assertThat(response.rspCode()).isEqualTo("99");
        assertThat(response.message()).doesNotContain("secret", "database");
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(TopUpOrderStatus.PENDING);
        assertThat(wallets.findByUserId(owner.getId())).isEmpty();
        assertThat(ledger.findByUserIdOrderByRecordedAtDesc(owner.getId())).isEmpty();
    }

    @Test
    void disabledPurchasesDoNotPersistOrdersButExistingPaymentsStillSettle() {
        var owner = user();
        var order = order(owner, TopUpOrderStatus.PENDING, Instant.now().plusSeconds(600));
        long count = orders.count();
        properties.setPaymentsEnabled(false);
        try {
            assertThatThrownBy(() -> orderService.createTopUpOrder(owner.getId(), UUID.randomUUID(), "new-key", "203.0.113.1"))
                    .isInstanceOf(com.codegym.aiplanning.common.exception.BusinessException.class);
            assertThat(orders.count()).isEqualTo(count);
            assertThat(notifications.processIpn(payment(order)).rspCode()).isEqualTo("00");
        } finally {
            properties.setPaymentsEnabled(true);
        }
    }

    @Test
    void concurrentWalletAccessAndConfirmationGrantBonusOnce() throws Exception {
        var owner = user();
        var order = order(owner, TopUpOrderStatus.PENDING, Instant.now().plusSeconds(600));
        var fields = payment(order);
        var executor = Executors.newFixedThreadPool(3);
        try {
            var requests = java.util.List.<java.util.concurrent.Callable<Object>>of(
                    () -> walletService.getOrCreateWallet(owner.getId()),
                    () -> notifications.processIpn(fields), () -> notifications.processIpn(fields));
            for (var future : executor.invokeAll(requests)) future.get();
        } finally {
            executor.shutdownNow();
        }
        assertThat(walletService.getOrCreateWallet(owner.getId()).availableCredits()).isEqualTo(6000);
        assertThat(ledger.findByUserIdOrderByRecordedAtDesc(owner.getId())).hasSize(2);
    }

    @Test
    void adminCannotAccessPersonalBillingEndpoints() throws Exception {
        var admin = jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        for (String path : java.util.List.of("wallet", "packages", "configuration", "ai-prices", "transactions",
                "top-up-orders/" + UUID.randomUUID(), "top-up-orders/by-code/TEST")) {
            mvc.perform(get("/api/v1/billing/" + path).with(admin)).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/billing/top-up-orders").with(admin)
                .header("Idempotency-Key", "test").contentType("application/json")
                .content("{\"packageId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void equalTimestampHistoryPagesUseStableIdTieBreaker() {
        var owner = user();
        walletService.getOrCreateWallet(owner.getId());
        var wallet = wallets.findByUserId(owner.getId()).orElseThrow();
        Instant recordedAt = Instant.parse("2026-10-11T12:00:00Z");
        for (int i = 1; i <= 2; i++) {
            var entry = CreditLedgerEntry.create(wallet, owner, LedgerEntryType.ADJUSTMENT, 1, 0,
                    1000 + i, 0, LedgerReferenceType.ACCOUNT, owner.getId(),
                    "TEST:" + UUID.randomUUID(), "Test adjustment");
            org.springframework.test.util.ReflectionTestUtils.setField(entry, "recordedAt", recordedAt);
            ledger.saveAndFlush(entry);
        }
        wallet.updateProjection(1002, 0);
        wallets.saveAndFlush(wallet);
        var first = walletService.getTransactions(owner.getId(), LedgerEntryType.ADJUSTMENT,
                null, null, PageRequest.of(0, 1));
        var second = walletService.getTransactions(owner.getId(), LedgerEntryType.ADJUSTMENT,
                null, null, PageRequest.of(1, 1));
        assertThat(first.getContent().get(0).id()).isNotEqualTo(second.getContent().get(0).id());
        assertThat(walletService.getTransactions(owner.getId(), LedgerEntryType.ADJUSTMENT,
                null, null, PageRequest.of(0, 1)).getContent()).isEqualTo(first.getContent());
    }

    @Test
    void historyRejectsInvalidDateAndSortInputs() {
        var owner = user();
        assertThatThrownBy(() -> walletService.getTransactions(owner.getId(), null, Instant.now(),
                Instant.now().minusSeconds(1), PageRequest.of(0, 10)))
                .isInstanceOf(com.codegym.aiplanning.common.exception.BusinessException.class);
        assertThatThrownBy(() -> walletService.getTransactions(owner.getId(), null, null, null,
                PageRequest.of(0, 10, org.springframework.data.domain.Sort.by("user.email"))))
                .isInstanceOf(com.codegym.aiplanning.common.exception.BusinessException.class);
    }
}
