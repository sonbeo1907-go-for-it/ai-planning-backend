package com.codegym.aiplanning.service.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.billing.dto.AiCreditRateResponse;
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
import com.codegym.aiplanning.service.billing.impl.CreditReservationServiceImpl;
import java.time.Instant;
import java.util.List;
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
class CreditReservationServiceTest {

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

    private CreditReservationServiceImpl creditReservationService;

    @BeforeEach
    void setUp() {
        creditReservationService = new CreditReservationServiceImpl(
                aiCreditRateRepository,
                aiCreditReservationRepository,
                creditWalletRepository,
                creditLedgerEntryRepository,
                creditWalletAtomicInitializer);
    }

    @Test
    @DisplayName("getActiveRates returns all active rates")
    void getActiveRates_returnsActiveRates() {
        AiCreditRate rate = AiCreditRate.create(
                AiPurpose.ROADMAP_GENERATION, null, 10L, AiCreditRateStatus.ACTIVE, Instant.now());
        when(aiCreditRateRepository.findByStatusOrderByPurposeAsc(AiCreditRateStatus.ACTIVE))
                .thenReturn(List.of(rate));

        List<AiCreditRateResponse> rates = creditReservationService.getActiveRates();

        assertThat(rates).hasSize(1);
        assertThat(rates.get(0).purpose()).isEqualTo(AiPurpose.ROADMAP_GENERATION);
        assertThat(rates.get(0).creditCost()).isEqualTo(10L);
    }

    @Test
    @DisplayName("validateSufficientCredits throws INSUFFICIENT_CREDITS when wallet balance is lower than cost")
    void validateSufficientCredits_throwsInsufficientCredits_whenBalanceTooLow() {
        UUID userId = UUID.randomUUID();
        UserAccount user = UserAccount.create("user@example.com", "hash", UserRole.USER, AccountStatus.ACTIVE);
        CreditWallet wallet = CreditWallet.createWithBalances(user, 5L, 0L);

        AiCreditRate rate = AiCreditRate.create(
                AiPurpose.ROADMAP_GENERATION, null, 10L, AiCreditRateStatus.ACTIVE, Instant.now());
        when(aiCreditRateRepository.findFirstByPurposeAndModelCategoryAndStatusOrderByEffectiveFromDesc(
                AiPurpose.ROADMAP_GENERATION, null, AiCreditRateStatus.ACTIVE))
                .thenReturn(Optional.of(rate));
        when(creditWalletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> creditReservationService.validateSufficientCredits(
                userId, AiPurpose.ROADMAP_GENERATION, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode())
                        .isEqualTo(ErrorCode.INSUFFICIENT_CREDITS));
    }

    @Test
    @DisplayName("reserveCredits atomically holds available credits and creates reservation & ledger entry")
    void reserveCredits_atomicallyHoldsCredits() {
        UUID userId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        UserAccount user = UserAccount.create("user@example.com", "hash", UserRole.USER, AccountStatus.ACTIVE);
        CreditWallet wallet = CreditWallet.createWithBalances(user, 50L, 0L);

        AiCreditRate rate = AiCreditRate.create(
                AiPurpose.ROADMAP_GENERATION, null, 10L, AiCreditRateStatus.ACTIVE, Instant.now());

        when(aiCreditReservationRepository.findByAiExecutionId(executionId)).thenReturn(Optional.empty());
        when(aiCreditRateRepository.findFirstByPurposeAndModelCategoryAndStatusOrderByEffectiveFromDesc(
                AiPurpose.ROADMAP_GENERATION, null, AiCreditRateStatus.ACTIVE))
                .thenReturn(Optional.of(rate));
        when(creditWalletRepository.findByUserIdForUpdate(user.getId())).thenReturn(Optional.of(wallet));
        when(aiCreditReservationRepository.save(any(AiCreditReservation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AiCreditReservation reservation = creditReservationService.reserveCredits(
                user, executionId, AiPurpose.ROADMAP_GENERATION, null);

        assertThat(reservation.getReservedCredits()).isEqualTo(10L);
        assertThat(reservation.getStatus()).isEqualTo(CreditReservationStatus.RESERVED);
        assertThat(wallet.getAvailableCredits()).isEqualTo(40L);
        assertThat(wallet.getReservedCredits()).isEqualTo(10L);

        ArgumentCaptor<CreditLedgerEntry> captor = ArgumentCaptor.forClass(CreditLedgerEntry.class);
        verify(creditLedgerEntryRepository).save(captor.capture());
        CreditLedgerEntry entry = captor.getValue();
        assertThat(entry.getEntryType()).isEqualTo(LedgerEntryType.RESERVE);
        assertThat(entry.getAvailableDelta()).isEqualTo(-10L);
        assertThat(entry.getReservedDelta()).isEqualTo(10L);
    }

    @Test
    @DisplayName("settleReservation moves reservation to SETTLED and deducts reservedCredits")
    void settleReservation_settlesCredits() {
        UUID executionId = UUID.randomUUID();
        UserAccount user = UserAccount.create("user@example.com", "hash", UserRole.USER, AccountStatus.ACTIVE);
        CreditWallet wallet = CreditWallet.createWithBalances(user, 40L, 10L);

        AiCreditReservation reservation = AiCreditReservation.reserve(
                wallet, user, executionId, UUID.randomUUID(), AiPurpose.ROADMAP_GENERATION, null, 10L, Instant.now());

        when(aiCreditReservationRepository.findWithLockByAiExecutionId(executionId)).thenReturn(Optional.of(reservation));
        when(creditWalletRepository.findByUserIdForUpdate(user.getId())).thenReturn(Optional.of(wallet));

        creditReservationService.settleReservation(executionId);

        assertThat(reservation.getStatus()).isEqualTo(CreditReservationStatus.SETTLED);
        assertThat(reservation.getChargedCredits()).isEqualTo(10L);
        assertThat(wallet.getReservedCredits()).isZero();
        assertThat(wallet.getAvailableCredits()).isEqualTo(40L);

        ArgumentCaptor<CreditLedgerEntry> captor = ArgumentCaptor.forClass(CreditLedgerEntry.class);
        verify(creditLedgerEntryRepository).save(captor.capture());
        CreditLedgerEntry entry = captor.getValue();
        assertThat(entry.getEntryType()).isEqualTo(LedgerEntryType.USAGE);
        assertThat(entry.getAvailableDelta()).isZero();
        assertThat(entry.getReservedDelta()).isEqualTo(-10L);
    }

    @Test
    @DisplayName("releaseReservation restores availableCredits and sets reservation to RELEASED")
    void releaseReservation_restoresCredits() {
        UUID executionId = UUID.randomUUID();
        UserAccount user = UserAccount.create("user@example.com", "hash", UserRole.USER, AccountStatus.ACTIVE);
        CreditWallet wallet = CreditWallet.createWithBalances(user, 40L, 10L);

        AiCreditReservation reservation = AiCreditReservation.reserve(
                wallet, user, executionId, UUID.randomUUID(), AiPurpose.ROADMAP_GENERATION, null, 10L, Instant.now());

        when(aiCreditReservationRepository.findWithLockByAiExecutionId(executionId)).thenReturn(Optional.of(reservation));
        when(creditWalletRepository.findByUserIdForUpdate(user.getId())).thenReturn(Optional.of(wallet));

        creditReservationService.releaseReservation(executionId);

        assertThat(reservation.getStatus()).isEqualTo(CreditReservationStatus.RELEASED);
        assertThat(wallet.getReservedCredits()).isZero();
        assertThat(wallet.getAvailableCredits()).isEqualTo(50L);

        ArgumentCaptor<CreditLedgerEntry> captor = ArgumentCaptor.forClass(CreditLedgerEntry.class);
        verify(creditLedgerEntryRepository).save(captor.capture());
        CreditLedgerEntry entry = captor.getValue();
        assertThat(entry.getEntryType()).isEqualTo(LedgerEntryType.RELEASE_RESERVE);
        assertThat(entry.getAvailableDelta()).isEqualTo(10L);
        assertThat(entry.getReservedDelta()).isEqualTo(-10L);
    }
}
