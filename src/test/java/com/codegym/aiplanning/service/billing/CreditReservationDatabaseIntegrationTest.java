package com.codegym.aiplanning.service.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.entity.ai.*;
import com.codegym.aiplanning.entity.auth.*;
import com.codegym.aiplanning.entity.billing.*;
import com.codegym.aiplanning.repository.ai.*;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.repository.billing.*;
import com.codegym.aiplanning.service.ai.execution.AiExecutionWorker;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:billing_credit_race;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
class CreditReservationDatabaseIntegrationTest {
    @Autowired private UserAccountRepository users;
    @Autowired private CreditWalletRepository wallets;
    @Autowired private CreditLedgerEntryRepository ledger;
    @Autowired private AiCreditReservationRepository reservations;
    @Autowired private AiExecutionRepository executions;
    @Autowired private AiProviderRepository providers;
    @Autowired private AiProviderConfigRepository configurations;
    @Autowired private CreditReservationService credits;
    @Autowired private CreditReservationRecovery recovery;
    @Autowired private TransactionTemplate transactions;
    @MockBean private AiExecutionWorker worker; // No external provider calls during financial tests.

    private UserAccount owner() {
        return users.saveAndFlush(UserAccount.create(UUID.randomUUID() + "@example.com", "hash",
                UserRole.USER, AccountStatus.ACTIVE));
    }

    private AiProviderConfig configuration() {
        var provider = providers.saveAndFlush(AiProvider.create("TEST_" + UUID.randomUUID().toString().replace("-", "").toUpperCase(java.util.Locale.ROOT),
                "Test provider", "https://api.example.com/v1", AiProviderProtocol.OPENAI_COMPATIBLE,
                CredentialSelectionStrategy.PRIORITY, true));
        return configurations.saveAndFlush(AiProviderConfig.create(provider, AiPurpose.DAILY_PLAN_GENERATION,
                "test-model", true, 60, 4096, 1024, new java.math.BigDecimal("0.2")));
    }

    private AiExecution queue(UserAccount owner, AiProviderConfig configuration) {
        return executions.saveAndFlush(AiExecution.queue(owner, configuration, AiPurpose.DAILY_PLAN_GENERATION,
                AiExecutionOperation.GENERATE, AiExecutionTargetType.DAILY_PLAN, UUID.randomUUID(), UUID.randomUUID().toString()));
    }

    @Test
    void firstAiRequestInitializesBonusAndReservesWithoutOpeningWalletScreen() {
        var owner = owner();
        var configuration = configuration();
        transactions.executeWithoutResult(status -> {
            credits.validateSufficientCredits(owner.getId(), AiPurpose.DAILY_PLAN_GENERATION, null);
            var execution = queue(owner, configuration);
            credits.reserveCredits(owner, execution.getId(), AiPurpose.DAILY_PLAN_GENERATION, null);
        });
        var wallet = wallets.findByUserId(owner.getId()).orElseThrow();
        assertThat(wallet.getAvailableCredits()).isEqualTo(995);
        assertThat(wallet.getReservedCredits()).isEqualTo(5);
        assertThat(ledger.findByUserIdOrderByRecordedAtDesc(owner.getId())).hasSize(2);
    }

    @Test
    void concurrentRequestsCannotOverspendAndRejectedExecutionRollsBack() throws Exception {
        var owner = owner();
        var configuration = configuration();
        wallets.saveAndFlush(CreditWallet.createWithBalances(owner, 5, 0));
        var executor = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Boolean> request = () -> {
                try {
                    transactions.executeWithoutResult(status -> {
                        var execution = queue(owner, configuration);
                        credits.reserveCredits(owner, execution.getId(), AiPurpose.DAILY_PLAN_GENERATION, null);
                    });
                    return true;
                } catch (BusinessException insufficient) {
                    assertThat(insufficient.errorCode()).isEqualTo(
                            com.codegym.aiplanning.common.exception.ErrorCode.INSUFFICIENT_CREDITS);
                    return false;
                }
            };
            var futures = executor.invokeAll(java.util.List.of(request, request));
            assertThat(java.util.List.of(futures.get(0).get(), futures.get(1).get())).containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }
        var wallet = wallets.findByUserId(owner.getId()).orElseThrow();
        assertThat(wallet.getAvailableCredits()).isZero();
        assertThat(wallet.getReservedCredits()).isEqualTo(5);
        assertThat(reservations.findByUserIdAndStatus(owner.getId(), CreditReservationStatus.RESERVED)).hasSize(1);
        assertThat(ledger.findByUserIdOrderByRecordedAtDesc(owner.getId())).hasSize(1);
        assertThat(executions.findAll().stream().filter(e -> e.getOwner().getId().equals(owner.getId())).count()).isEqualTo(1);
    }

    @Test
    void failureAfterReservationRollsBackExecutionWalletBonusAndLedger() {
        var owner = owner();
        var configuration = configuration();
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            var execution = queue(owner, configuration);
            credits.reserveCredits(owner, execution.getId(), AiPurpose.DAILY_PLAN_GENERATION, null);
            throw new IllegalStateException("Simulated input persistence failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(wallets.findByUserId(owner.getId())).isEmpty();
        assertThat(ledger.findByUserIdOrderByRecordedAtDesc(owner.getId())).isEmpty();
        assertThat(reservations.findByUserIdAndStatus(owner.getId(), CreditReservationStatus.RESERVED)).isEmpty();
        assertThat(executions.findAll().stream().filter(e -> e.getOwner().getId().equals(owner.getId())).count()).isZero();
    }

    @Test
    void repeatedReservationAndReleaseDoNotDuplicateFinancialEvents() {
        var owner = owner();
        var execution = queue(owner, configuration());
        credits.reserveCredits(owner, execution.getId(), AiPurpose.DAILY_PLAN_GENERATION, null);
        credits.reserveCredits(owner, execution.getId(), AiPurpose.DAILY_PLAN_GENERATION, null);
        credits.releaseReservation(execution.getId());
        credits.releaseReservation(execution.getId());
        assertThat(wallets.findByUserId(owner.getId()).orElseThrow().getAvailableCredits()).isEqualTo(1000);
        assertThat(ledger.findByUserIdOrderByRecordedAtDesc(owner.getId())).hasSize(3);
    }

    @Test
    void terminalReservationRecoveryReleasesExactlyOnceButLeavesQueuedWorkReserved() {
        var owner = owner();
        var configuration = configuration();
        var failed = queue(owner, configuration);
        var queued = queue(owner, configuration);
        credits.reserveCredits(owner, failed.getId(), AiPurpose.DAILY_PLAN_GENERATION, null);
        credits.reserveCredits(owner, queued.getId(), AiPurpose.DAILY_PLAN_GENERATION, null);
        transactions.executeWithoutResult(status -> {
            executions.claimQueued(failed.getId(), Instant.now(), Instant.now().plusSeconds(600));
            var execution = executions.findByIdForUpdate(failed.getId()).orElseThrow();
            execution.markFailed("TEST_FAILURE", "Legacy unsettled failure", Instant.now());
            executions.saveAndFlush(execution);
        });
        recovery.reconcileTerminalExecutions();
        recovery.reconcileTerminalExecutions();
        assertThat(reservations.findByAiExecutionId(failed.getId()).orElseThrow().getStatus())
                .isEqualTo(CreditReservationStatus.RELEASED);
        assertThat(reservations.findByAiExecutionId(queued.getId()).orElseThrow().getStatus())
                .isEqualTo(CreditReservationStatus.RESERVED);
        assertThat(wallets.findByUserId(owner.getId()).orElseThrow().getReservedCredits()).isEqualTo(5);
    }
}
