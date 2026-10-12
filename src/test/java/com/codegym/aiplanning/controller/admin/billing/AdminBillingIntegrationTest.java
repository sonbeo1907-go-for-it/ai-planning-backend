package com.codegym.aiplanning.controller.admin.billing;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.admin.billing.dto.AdminBillingDtos.*;
import com.codegym.aiplanning.entity.ai.*;
import com.codegym.aiplanning.entity.auth.*;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.billing.*;
import com.codegym.aiplanning.repository.ai.*;
import com.codegym.aiplanning.repository.audit.AuditLogRepository;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.repository.billing.*;
import com.codegym.aiplanning.service.ai.AiProviderSelector;
import com.codegym.aiplanning.service.ai.execution.AiExecutionWorker;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.service.billing.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:admin_billing;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminBillingIntegrationTest {
    @Autowired private AdminBillingCatalogService catalog;
    @Autowired private AdminCreditAdjustmentService adjustments;
    @Autowired private BillingOrderService ordersService;
    @Autowired private UserAccountRepository users;
    @Autowired private CreditPackageRepository packages;
    @Autowired private TopUpOrderRepository orders;
    @Autowired private AiCreditRateRepository rates;
    @Autowired private CreditWalletRepository wallets;
    @Autowired private CreditLedgerEntryRepository ledger;
    @Autowired private AuditLogRepository auditLogs;
    @Autowired private AiProviderRepository providers;
    @Autowired private AiProviderConfigRepository configs;
    @Autowired private AiExecutionRepository executions;
    @Autowired private AiCreditReservationRepository reservations;
    @Autowired private CreditReservationService credits;
    @Autowired private AiCreditQuoteService quotes;
    @Autowired private MockMvc mvc;
    @Autowired private TopUpOrderConfirmationTxService confirmationService;
    @SpyBean private AuditLogService audit;
    @SpyBean private AiProviderSelector providerSelector;
    @MockBean private AiExecutionWorker worker;

    private UserAccount account(UserRole role) {
        return users.saveAndFlush(UserAccount.create(UUID.randomUUID() + "@example.com", "hash", role, AccountStatus.ACTIVE));
    }

    private PackageInput packageInput(String code, Long version, long price) {
        return new PackageInput(code, "Gói kiểm thử", price, 5000, 100, 0, version);
    }

    private RateView draft(UserAccount actor, String category, long cost) {
        return catalog.createRate(actor.getId(), actor.getEmail(), new RateInput(AiPurpose.DAILY_PLAN_GENERATION, category, cost, null));
    }

    private RateView activate(UserAccount actor, RateView rate, RateView current) {
        return catalog.rateStatus(actor.getId(), actor.getEmail(), rate.id(), new RateStatusInput(rate.version(),
                AiCreditRateStatus.ACTIVE, current == null ? null : current.id(), current == null ? null : current.version()));
    }

    @Test
    void userIsForbiddenFromAllAdminBillingRoutes() throws Exception {
        var user = account(UserRole.USER);
        var auth = jwt().jwt(token -> token.subject(user.getId().toString()).claim("email", user.getEmail()))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
        for (String path : List.of("/packages", "/ai-rates", "/wallets/lookup?account=x", "/wallets/" + user.getId(),
                "/ai-rates/" + UUID.randomUUID() + "/activation-preview")) {
            mvc.perform(get("/api/v1/admin/billing" + path).with(auth)).andExpect(status().isForbidden());
        }
        for (String path : List.of("/packages", "/ai-rates", "/wallets/" + user.getId() + "/adjustments")) {
            mvc.perform(post("/api/v1/admin/billing" + path).with(auth).contentType("application/json")
                    .content("{}").header("Idempotency-Key", "test")).andExpect(status().isForbidden());
        }
        for (String path : List.of("/packages/" + UUID.randomUUID(), "/ai-rates/" + UUID.randomUUID())) {
            mvc.perform(put("/api/v1/admin/billing" + path).with(auth).contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
            mvc.perform(patch("/api/v1/admin/billing" + path + "/status").with(auth).contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void packageChangesPreserveOrderSnapshotAndReferencedPackageCannotBeDeleted() {
        var actor = account(UserRole.ADMIN);
        var user = account(UserRole.USER);
        String code = "TEST_" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        var item = catalog.createPackage(actor.getId(), actor.getEmail(), packageInput(code, null, 50000));
        item = catalog.packageStatus(actor.getId(), actor.getEmail(), item.id(), new PackageStatusInput(item.version(), CreditPackageStatus.ACTIVE));
        var order = ordersService.createTopUpOrder(user.getId(), item.id(), UUID.randomUUID().toString(), "127.0.0.1");
        var changed = catalog.updatePackage(actor.getId(), actor.getEmail(), item.id(), packageInput(code, item.version(), 60000));
        catalog.packageStatus(actor.getId(), actor.getEmail(), changed.id(), new PackageStatusInput(changed.version(), CreditPackageStatus.ARCHIVED));
        assertThat(orders.findById(order.id()).orElseThrow().getPriceVndSnapshot()).isEqualTo(50000);
        UUID packageId = item.id();
        assertThatThrownBy(() -> packages.deleteById(packageId)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(auditLogs.findAll()).anySatisfy(log -> {
            assertThat(log.getActorId()).isEqualTo(actor.getId());
            assertThat(log.getAction()).isEqualTo(AuditEventAction.CREDIT_PACKAGE_UPDATED);
            assertThat(log.getTargetId()).isEqualTo(packageId.toString());
        });
    }

    @Test
    void staleEditsAndUnsafeTotalsAreRejected() {
        var actor = account(UserRole.ADMIN);
        String code = "TEST_" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        var item = catalog.createPackage(actor.getId(), actor.getEmail(), packageInput(code, null, 50000));
        assertThatThrownBy(() -> catalog.updatePackage(actor.getId(), actor.getEmail(), item.id(), packageInput(code, 99L, 50000)))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION));
        assertThatThrownBy(() -> catalog.createPackage(actor.getId(), actor.getEmail(),
                new PackageInput(code + "X", "Invalid", 50000, BillingValidation.MAX_SAFE_INTEGER, 1, 0, null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void replacementPreservesReservationSnapshotAndQuoteUsesProviderCategory() {
        var actor = account(UserRole.ADMIN);
        var user = account(UserRole.USER);
        String category = "CAT_" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        var original = activate(actor, draft(actor, category, 7), null);
        var provider = providers.saveAndFlush(AiProvider.create(category, "Test", "https://api.example.com/v1",
                AiProviderProtocol.OPENAI_COMPATIBLE, CredentialSelectionStrategy.PRIORITY, true));
        var configuration = AiProviderConfig.create(provider, AiPurpose.DAILY_PLAN_GENERATION, "test", true, 60, 4096, 1024, BigDecimal.ZERO);
        configuration.setPricingCategory(category);
        configuration = configs.saveAndFlush(configuration);
        doReturn(configuration).when(providerSelector).requireDefault(AiPurpose.DAILY_PLAN_GENERATION);
        assertThat(quotes.quote(AiPurpose.DAILY_PLAN_GENERATION).creditCost()).isEqualTo(7);
        var execution = executions.saveAndFlush(AiExecution.queue(user, configuration, AiPurpose.DAILY_PLAN_GENERATION,
                AiExecutionOperation.GENERATE, AiExecutionTargetType.DAILY_PLAN, UUID.randomUUID(), UUID.randomUUID().toString()));
        credits.reserveCredits(user, execution.getId(), AiPurpose.DAILY_PLAN_GENERATION, category);
        var replacement = catalog.replaceRate(actor.getId(), actor.getEmail(), original.id(),
                new RateInput(original.purpose(), category, 12, original.version()));
        assertThat(replacement.id()).isNotEqualTo(original.id());
        assertThat(quotes.quote(AiPurpose.DAILY_PLAN_GENERATION).creditCost()).isEqualTo(12);
        var reservation = reservations.findByAiExecutionId(execution.getId()).orElseThrow();
        assertThat(reservation.getReservedCredits()).isEqualTo(7);
        assertThat(rates.findById(original.id()).orElseThrow().getCreditCost()).isEqualTo(7);
        assertThatThrownBy(() -> rates.deleteById(original.id())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void concurrentActivationsLeaveOneActiveRate() throws Exception {
        var actor = account(UserRole.ADMIN);
        String category = "RACE_" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        var first = draft(actor, category, 5);
        var second = draft(actor, category, 8);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var calls = List.<java.util.concurrent.Callable<Boolean>>of(() -> tryActivate(actor, first), () -> tryActivate(actor, second));
            var results = pool.invokeAll(calls);
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(true, false);
        } finally {
            pool.shutdownNow();
        }
        assertThat(rates.findAll()).filteredOn(rate -> category.equals(rate.getModelCategory()) && rate.getStatus() == AiCreditRateStatus.ACTIVE).hasSize(1);
    }

    private boolean tryActivate(UserAccount actor, RateView rate) {
        try {
            activate(actor, rate, null);
            return true;
        } catch (BusinessException conflict) {
            assertThat(conflict.errorCode()).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION);
            return false;
        }
    }

    @Test
    void databaseRejectsDuplicateDefaultAndCategorySlots() {
        var duplicate = AiCreditRate.create(AiPurpose.DAILY_PLAN_GENERATION, null, 99, AiCreditRateStatus.ACTIVE, Instant.now());
        assertThatThrownBy(() -> rates.saveAndFlush(duplicate)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        String category = UUID.randomUUID().toString().toUpperCase();
        rates.saveAndFlush(AiCreditRate.create(AiPurpose.DAILY_PLAN_GENERATION, category, 9, AiCreditRateStatus.ACTIVE, Instant.now()));
        assertThatThrownBy(() -> rates.saveAndFlush(AiCreditRate.create(AiPurpose.DAILY_PLAN_GENERATION, category.toLowerCase(), 10,
                AiCreditRateStatus.ACTIVE, Instant.now()))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void adjustmentIsIdempotentAuditedAndNeverChangesReservedCredits() {
        var actor = account(UserRole.ADMIN);
        var user = account(UserRole.USER);
        var wallet = wallets.saveAndFlush(CreditWallet.createWithBalances(user, 100, 20));
        var input = new AdjustmentInput(-10, "Support correction", wallet.getVersion());
        var result = adjustments.adjust(actor.getId(), actor.getEmail(), user.getId(), "support-1", input);
        var repeat = adjustments.adjust(actor.getId(), actor.getEmail(), user.getId(), "support-1", input);
        assertThat(result.availableCredits()).isEqualTo(90);
        assertThat(result.reservedCredits()).isEqualTo(20);
        assertThat(repeat.replayed()).isTrue();
        assertThat(repeat.ledgerEntryId()).isEqualTo(result.ledgerEntryId());
        assertThat(ledger.findByUserIdOrderByRecordedAtDesc(user.getId())).hasSize(1);
        assertThat(auditLogs.findAll()).filteredOn(log -> result.ledgerEntryId().toString().equals(log.getTargetId()))
                .singleElement().satisfies(log -> {
                    assertThat(log.getActorId()).isEqualTo(actor.getId());
                    assertThat(log.getAction()).isEqualTo(AuditEventAction.CREDIT_WALLET_ADJUSTED);
                });
        assertThatThrownBy(() -> adjustments.adjust(actor.getId(), actor.getEmail(), user.getId(), "support-1",
                new AdjustmentInput(10, "Support correction", wallet.getVersion()))).isInstanceOf(BusinessException.class);
        var latest = wallets.findByUserId(user.getId()).orElseThrow();
        assertThatThrownBy(() -> adjustments.adjust(actor.getId(), actor.getEmail(), user.getId(), "support-2",
                new AdjustmentInput(-91, "Too much", latest.getVersion()))).isInstanceOf(BusinessException.class);
    }

    @Test
    void auditFailureRollsBackFirstWalletWelcomeBonusAndAdjustment() {
        var actor = account(UserRole.ADMIN);
        var user = account(UserRole.USER);
        doThrow(new IllegalStateException("Audit unavailable")).when(audit).logAction(eq(actor.getId()), anyString(),
                eq(AuditEventAction.CREDIT_WALLET_ADJUSTED), anyString(), anyString());
        assertThatThrownBy(() -> adjustments.adjust(actor.getId(), actor.getEmail(), user.getId(), "rollback",
                new AdjustmentInput(100, "Correction", null))).isInstanceOf(IllegalStateException.class);
        assertThat(wallets.findByUserId(user.getId())).isEmpty();
        assertThat(ledger.findByUserIdOrderByRecordedAtDesc(user.getId())).isEmpty();
    }

    @Test
    void lookupDoesNotInitializeWalletAndDoesNotExposeAdminAccounts() {
        var user = account(UserRole.USER);
        var actor = account(UserRole.ADMIN);
        assertThat(adjustments.lookup(user.getEmail().toUpperCase()).initialized()).isFalse();
        assertThat(wallets.findByUserId(user.getId())).isEmpty();
        assertThatThrownBy(() -> adjustments.lookup(actor.getId().toString())).isInstanceOf(BusinessException.class);
    }

    @Test
    void adminHttpValidatesBodiesAndReturnsPaginatedContract() throws Exception {
        var actor = account(UserRole.ADMIN);
        var auth = jwt().jwt(token -> token.subject(actor.getId().toString()).claim("email", actor.getEmail()))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        mvc.perform(get("/api/v1/admin/billing/packages?size=1000").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.size").value(100));
        mvc.perform(post("/api/v1/admin/billing/packages").with(auth).contentType("application/json")
                .content("{\"packageCode\":\"invalid\",\"name\":\"Test\",\"priceVnd\":50000,\"baseCredits\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/admin/billing/packages/" + UUID.randomUUID() + "/status").with(auth)
                .contentType("application/json").content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void concurrentAdjustmentAndAiReservationKeepWalletAndLedgerConsistent() throws Exception {
        var actor = account(UserRole.ADMIN);
        var user = account(UserRole.USER);
        var wallet = wallets.saveAndFlush(CreditWallet.createWithBalances(user, 100, 0));
        String code = "PROVIDER_" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        var provider = providers.saveAndFlush(AiProvider.create(code, "Test", "https://api.example.com/v1",
                AiProviderProtocol.OPENAI_COMPATIBLE, CredentialSelectionStrategy.PRIORITY, true));
        var config = configs.saveAndFlush(AiProviderConfig.create(provider, AiPurpose.DAILY_PLAN_GENERATION,
                "test", true, 60, 4096, 1024, BigDecimal.ZERO));
        var execution = executions.saveAndFlush(AiExecution.queue(user, config, AiPurpose.DAILY_PLAN_GENERATION,
                AiExecutionOperation.GENERATE, AiExecutionTargetType.DAILY_PLAN, UUID.randomUUID(), UUID.randomUUID().toString()));
        runAlongsideAdjustment(actor, user, wallet.getVersion(), () ->
                credits.reserveCredits(user, execution.getId(), AiPurpose.DAILY_PLAN_GENERATION, null));
        var updated = wallets.findByUserId(user.getId()).orElseThrow();
        assertThat(updated.getReservedCredits()).isEqualTo(5);
        assertThat(updated.getAvailableCredits()).isIn(95L, 105L);
        var entries = ledger.findByUserIdOrderByRecordedAtDesc(user.getId());
        assertThat(100 + entries.stream().mapToLong(CreditLedgerEntry::getAvailableDelta).sum()).isEqualTo(updated.getAvailableCredits());
    }

    @Test
    void concurrentAdjustmentAndTopUpDoNotLoseEitherBalanceChange() throws Exception {
        var actor = account(UserRole.ADMIN);
        var user = account(UserRole.USER);
        var wallet = wallets.saveAndFlush(CreditWallet.createWithBalances(user, 100, 20));
        String code = "TOPUP-" + UUID.randomUUID();
        orders.saveAndFlush(new TopUpOrder(user.getId(), code, null, "TEST", "Test credits", 50000,
                5000, 0, 5000, TopUpOrderStatus.PENDING, "VND", "VNPAY", code,
                UUID.randomUUID().toString(), Instant.now().plusSeconds(600)));
        String transactionId = String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits() % 999999999L) + 1);
        runAlongsideAdjustment(actor, user, wallet.getVersion(), () -> {
            var payment = new com.codegym.aiplanning.service.billing.gateway.VerifiedPayment(
                    "VNPAY", code, transactionId, 5000000, "00", "00", false);
            assertThat(confirmationService.confirmPayment(payment).rspCode()).isEqualTo("00");
        });
        var updated = wallets.findByUserId(user.getId()).orElseThrow();
        assertThat(updated.getReservedCredits()).isEqualTo(20);
        assertThat(updated.getAvailableCredits()).isIn(5100L, 5110L);
        var entries = ledger.findByUserIdOrderByRecordedAtDesc(user.getId());
        assertThat(100 + entries.stream().mapToLong(CreditLedgerEntry::getAvailableDelta).sum()).isEqualTo(updated.getAvailableCredits());
    }

    private void runAlongsideAdjustment(UserAccount actor, UserAccount user, long version, Runnable operation) throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            var calls = List.<java.util.concurrent.Callable<Void>>of(() -> {
                try {
                    adjustments.adjust(actor.getId(), actor.getEmail(), user.getId(), UUID.randomUUID().toString(),
                            new AdjustmentInput(10, "Concurrent support adjustment", version));
                } catch (BusinessException conflict) {
                    assertThat(conflict.errorCode()).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION);
                }
                return null;
            }, () -> { operation.run(); return null; });
            for (var result : pool.invokeAll(calls)) {
                result.get();
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
