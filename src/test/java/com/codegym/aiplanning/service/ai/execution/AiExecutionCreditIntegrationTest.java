package com.codegym.aiplanning.service.ai.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.entity.ai.AiPurpose;
import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.entity.billing.AiCreditRate;
import com.codegym.aiplanning.entity.billing.AiCreditRateStatus;
import com.codegym.aiplanning.entity.billing.AiCreditReservation;
import com.codegym.aiplanning.entity.billing.CreditLedgerEntry;
import com.codegym.aiplanning.entity.billing.CreditReservationStatus;
import com.codegym.aiplanning.entity.billing.CreditWallet;
import com.codegym.aiplanning.entity.billing.LedgerEntryType;
import com.codegym.aiplanning.repository.billing.AiCreditRateRepository;
import com.codegym.aiplanning.repository.billing.AiCreditReservationRepository;
import com.codegym.aiplanning.repository.billing.CreditLedgerEntryRepository;
import com.codegym.aiplanning.repository.billing.CreditWalletRepository;
import com.codegym.aiplanning.service.billing.CreditReservationService;
import com.codegym.aiplanning.service.billing.CreditWalletAtomicInitializer;
import com.codegym.aiplanning.service.billing.impl.CreditReservationServiceImpl;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiExecutionCreditIntegrationTest {

    @Mock
    private AiCreditRateRepository aiCreditRateRepository;

    @Mock
    private AiCreditReservationRepository aiCreditReservationRepository;

    @Mock
    private CreditWalletRepository creditWalletRepository;

    @Mock
    private CreditLedgerEntryRepository creditLedgerEntryRepository;

    @Mock
    private CreditWalletAtomicInitializer creditWalletAtomicInitializer;

    private CreditReservationService creditReservationService;

    private UserAccount testUser;
    private CreditWallet testWallet;
    private AiCreditRate roadmapRate;

    @BeforeEach
    void setUp() {
        creditReservationService = new CreditReservationServiceImpl(
                aiCreditRateRepository,
                aiCreditReservationRepository,
                creditWalletRepository,
                creditLedgerEntryRepository,
                creditWalletAtomicInitializer);

        testUser = UserAccount.create("student@example.com", "hash", UserRole.USER, AccountStatus.ACTIVE);
        testWallet = CreditWallet.createWithBalances(testUser, 20L, 0L); // 20 credits available
        roadmapRate = AiCreditRate.create(AiPurpose.ROADMAP_GENERATION, null, 10L, AiCreditRateStatus.ACTIVE, Instant.now());
    }

    @Test
    @DisplayName("Pre-queue validation fails with INSUFFICIENT_CREDITS when wallet balance is lower than cost")
    void preQueueValidation_insufficientCredits_blocksQueueing() {
        when(aiCreditRateRepository.findFirstByPurposeAndModelCategoryAndStatusOrderByEffectiveFromDesc(
                AiPurpose.ROADMAP_GENERATION, null, AiCreditRateStatus.ACTIVE))
                .thenReturn(Optional.of(roadmapRate));

        CreditWallet poorWallet = CreditWallet.createWithBalances(testUser, 5L, 0L); // only 5 credits, need 10
        when(creditWalletRepository.findByUserId(testUser.getId())).thenReturn(Optional.of(poorWallet));

        assertThatThrownBy(() -> creditReservationService.validateSufficientCredits(
                testUser.getId(), AiPurpose.ROADMAP_GENERATION, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.errorCode()).isEqualTo(ErrorCode.INSUFFICIENT_CREDITS);
                    // Ensure error message does not expose internal provider credentials
                    assertThat(be.getMessage()).doesNotContain("apiKey").doesNotContain("secret");
                });
    }

    @Test
    @DisplayName("Atomic credit reservation holds credits and links reservation & ledger to aiExecutionId")
    void reserveCredits_atomicallyHoldsCreditsAndLinksExecution() {
        UUID aiExecutionId = UUID.randomUUID();

        when(aiCreditReservationRepository.findByAiExecutionId(aiExecutionId)).thenReturn(Optional.empty());
        when(aiCreditRateRepository.findFirstByPurposeAndModelCategoryAndStatusOrderByEffectiveFromDesc(
                AiPurpose.ROADMAP_GENERATION, null, AiCreditRateStatus.ACTIVE))
                .thenReturn(Optional.of(roadmapRate));
        when(creditWalletRepository.findByUserIdForUpdate(testUser.getId())).thenReturn(Optional.of(testWallet));
        when(aiCreditReservationRepository.save(any(AiCreditReservation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AiCreditReservation reservation = creditReservationService.reserveCredits(
                testUser, aiExecutionId, AiPurpose.ROADMAP_GENERATION, null);

        // Verification of state
        assertThat(reservation.getAiExecutionId()).isEqualTo(aiExecutionId);
        assertThat(reservation.getReservedCredits()).isEqualTo(10L);
        assertThat(reservation.getStatus()).isEqualTo(CreditReservationStatus.RESERVED);
        assertThat(testWallet.getAvailableCredits()).isEqualTo(10L); // 20 - 10
        assertThat(testWallet.getReservedCredits()).isEqualTo(10L);

        ArgumentCaptor<CreditLedgerEntry> captor = ArgumentCaptor.forClass(CreditLedgerEntry.class);
        verify(creditLedgerEntryRepository).save(captor.capture());
        CreditLedgerEntry entry = captor.getValue();
        assertThat(entry.getEntryType()).isEqualTo(LedgerEntryType.RESERVE);
        assertThat(entry.getReferenceId()).isEqualTo(aiExecutionId);
        assertThat(entry.getAvailableDelta()).isEqualTo(-10L);
        assertThat(entry.getReservedDelta()).isEqualTo(10L);
    }

    @Test
    @DisplayName("Worker successful execution settles reservation into USAGE")
    void workerSuccess_settlesReservationToUsage() {
        UUID aiExecutionId = UUID.randomUUID();
        testWallet.reserveCredits(10L); // wallet: 10 avail, 10 reserved

        AiCreditReservation reservation = AiCreditReservation.reserve(
                testWallet, testUser, aiExecutionId, roadmapRate.getId(),
                AiPurpose.ROADMAP_GENERATION, null, 10L, Instant.now());

        when(aiCreditReservationRepository.findWithLockByAiExecutionId(aiExecutionId))
                .thenReturn(Optional.of(reservation));
        when(creditWalletRepository.findByUserIdForUpdate(testUser.getId()))
                .thenReturn(Optional.of(testWallet));

        creditReservationService.settleReservation(aiExecutionId);

        assertThat(reservation.getStatus()).isEqualTo(CreditReservationStatus.SETTLED);
        assertThat(reservation.getChargedCredits()).isEqualTo(10L);
        assertThat(testWallet.getReservedCredits()).isZero();
        assertThat(testWallet.getAvailableCredits()).isEqualTo(10L);

        ArgumentCaptor<CreditLedgerEntry> captor = ArgumentCaptor.forClass(CreditLedgerEntry.class);
        verify(creditLedgerEntryRepository).save(captor.capture());
        CreditLedgerEntry entry = captor.getValue();
        assertThat(entry.getEntryType()).isEqualTo(LedgerEntryType.USAGE);
        assertThat(entry.getReferenceId()).isEqualTo(aiExecutionId);
    }

    @Test
    @DisplayName("Worker failure releases reservation back to available credits")
    void workerFailure_releasesReservationBackToAvailable() {
        UUID aiExecutionId = UUID.randomUUID();
        testWallet.reserveCredits(10L); // wallet: 10 avail, 10 reserved

        AiCreditReservation reservation = AiCreditReservation.reserve(
                testWallet, testUser, aiExecutionId, roadmapRate.getId(),
                AiPurpose.ROADMAP_GENERATION, null, 10L, Instant.now());

        when(aiCreditReservationRepository.findWithLockByAiExecutionId(aiExecutionId))
                .thenReturn(Optional.of(reservation));
        when(creditWalletRepository.findByUserIdForUpdate(testUser.getId()))
                .thenReturn(Optional.of(testWallet));

        creditReservationService.releaseReservation(aiExecutionId);

        assertThat(reservation.getStatus()).isEqualTo(CreditReservationStatus.RELEASED);
        assertThat(testWallet.getReservedCredits()).isZero();
        assertThat(testWallet.getAvailableCredits()).isEqualTo(20L); // 10 + 10 restored

        ArgumentCaptor<CreditLedgerEntry> captor = ArgumentCaptor.forClass(CreditLedgerEntry.class);
        verify(creditLedgerEntryRepository).save(captor.capture());
        CreditLedgerEntry entry = captor.getValue();
        assertThat(entry.getEntryType()).isEqualTo(LedgerEntryType.RELEASE_RESERVE);
        assertThat(entry.getReferenceId()).isEqualTo(aiExecutionId);
    }

    @Test
    @DisplayName("Automatic retries within same AiExecution do not charge extra (idempotent)")
    void retryWithinSameExecution_doesNotChargeExtra() {
        UUID aiExecutionId = UUID.randomUUID();
        AiCreditReservation existingReservation = AiCreditReservation.reserve(
                testWallet, testUser, aiExecutionId, roadmapRate.getId(),
                AiPurpose.ROADMAP_GENERATION, null, 10L, Instant.now());

        when(aiCreditReservationRepository.findByAiExecutionId(aiExecutionId))
                .thenReturn(Optional.of(existingReservation));

        AiCreditReservation result = creditReservationService.reserveCredits(
                testUser, aiExecutionId, AiPurpose.ROADMAP_GENERATION, null);

        assertThat(result).isSameAs(existingReservation);
        // Wallet was untouched
        assertThat(testWallet.getAvailableCredits()).isEqualTo(20L);
    }
}
