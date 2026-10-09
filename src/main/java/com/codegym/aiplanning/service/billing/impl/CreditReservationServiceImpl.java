package com.codegym.aiplanning.service.billing.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.billing.dto.AiCreditRateResponse;
import com.codegym.aiplanning.entity.ai.AiPurpose;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.billing.AiCreditRate;
import com.codegym.aiplanning.entity.billing.AiCreditRateStatus;
import com.codegym.aiplanning.entity.billing.AiCreditReservation;
import com.codegym.aiplanning.entity.billing.CreditLedgerEntry;
import com.codegym.aiplanning.entity.billing.CreditReservationStatus;
import com.codegym.aiplanning.entity.billing.CreditWallet;
import com.codegym.aiplanning.entity.billing.LedgerEntryType;
import com.codegym.aiplanning.entity.billing.LedgerReferenceType;
import com.codegym.aiplanning.repository.billing.AiCreditRateRepository;
import com.codegym.aiplanning.repository.billing.AiCreditReservationRepository;
import com.codegym.aiplanning.repository.billing.CreditLedgerEntryRepository;
import com.codegym.aiplanning.repository.billing.CreditWalletRepository;
import com.codegym.aiplanning.service.billing.CreditReservationService;
import com.codegym.aiplanning.service.billing.CreditWalletAtomicInitializer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreditReservationServiceImpl implements CreditReservationService {

    private final AiCreditRateRepository aiCreditRateRepository;
    private final AiCreditReservationRepository aiCreditReservationRepository;
    private final CreditWalletRepository creditWalletRepository;
    private final CreditLedgerEntryRepository creditLedgerEntryRepository;
    private final CreditWalletAtomicInitializer creditWalletAtomicInitializer;

    public CreditReservationServiceImpl(
            AiCreditRateRepository aiCreditRateRepository,
            AiCreditReservationRepository aiCreditReservationRepository,
            CreditWalletRepository creditWalletRepository,
            CreditLedgerEntryRepository creditLedgerEntryRepository,
            CreditWalletAtomicInitializer creditWalletAtomicInitializer) {
        this.aiCreditRateRepository = aiCreditRateRepository;
        this.aiCreditReservationRepository = aiCreditReservationRepository;
        this.creditWalletRepository = creditWalletRepository;
        this.creditLedgerEntryRepository = creditLedgerEntryRepository;
        this.creditWalletAtomicInitializer = creditWalletAtomicInitializer;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiCreditRateResponse> getActiveRates() {
        return aiCreditRateRepository.findByStatusOrderByPurposeAsc(AiCreditRateStatus.ACTIVE)
                .stream()
                .map(AiCreditRateResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public void validateSufficientCredits(
            UUID userId,
            AiPurpose purpose,
            String modelCategory) {
        AiCreditRate rate = findRateOrThrow(purpose, modelCategory);

        long available = creditWalletRepository.findByUserId(userId)
                .map(CreditWallet::getAvailableCredits)
                .orElse(0L);

        if (available < rate.getCreditCost()) {
            throw new BusinessException(
                    ErrorCode.INSUFFICIENT_CREDITS,
                    "Số dư AI Credit không đủ để thực hiện thao tác (Cần "
                            + rate.getCreditCost() + " credit, khả dụng: "
                            + available + " credit).");
        }
    }

    @Override
    @Transactional
    public AiCreditReservation reserveCredits(
            UserAccount user,
            UUID aiExecutionId,
            AiPurpose purpose,
            String modelCategory) {
        var existing = aiCreditReservationRepository.findByAiExecutionId(aiExecutionId);
        if (existing.isPresent()) {
            return existing.get();
        }

        AiCreditRate rate = findRateOrThrow(purpose, modelCategory);

        CreditWallet wallet = creditWalletRepository
                .findByUserIdForUpdate(user.getId())
                .orElseGet(() -> {
                    creditWalletAtomicInitializer.createWalletWithWelcomeBonus(user);
                    return creditWalletRepository.findByUserIdForUpdate(user.getId())
                            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Wallet not found"));
                });

        if (wallet.getAvailableCredits() < rate.getCreditCost()) {
            throw new BusinessException(
                    ErrorCode.INSUFFICIENT_CREDITS,
                    "Số dư AI Credit không đủ để thực hiện thao tác (Cần "
                            + rate.getCreditCost() + " credit, khả dụng: "
                            + wallet.getAvailableCredits() + " credit).");
        }

        wallet.reserveCredits(rate.getCreditCost());
        creditWalletRepository.save(wallet);

        AiCreditReservation reservation = AiCreditReservation.reserve(
                wallet,
                user,
                aiExecutionId,
                rate.getId(),
                purpose,
                modelCategory,
                rate.getCreditCost(),
                Instant.now());
        reservation = aiCreditReservationRepository.save(reservation);

        CreditLedgerEntry entry = CreditLedgerEntry.create(
                wallet,
                user,
                LedgerEntryType.RESERVE,
                -rate.getCreditCost(),
                rate.getCreditCost(),
                wallet.getAvailableCredits(),
                wallet.getReservedCredits(),
                LedgerReferenceType.AI_EXECUTION,
                aiExecutionId,
                "RESERVE:AI_EXECUTION:" + aiExecutionId,
                "Giữ chỗ credit cho thao tác AI: " + purpose);
        creditLedgerEntryRepository.save(entry);

        return reservation;
    }

    @Override
    @Transactional
    public void settleReservation(UUID aiExecutionId) {
        var reservationOpt = aiCreditReservationRepository.findWithLockByAiExecutionId(aiExecutionId);
        if (reservationOpt.isEmpty()) {
            return;
        }

        AiCreditReservation reservation = reservationOpt.get();
        if (reservation.getStatus() != CreditReservationStatus.RESERVED) {
            return;
        }

        CreditWallet wallet = creditWalletRepository
                .findByUserIdForUpdate(reservation.getUser().getId())
                .orElseThrow(() -> new IllegalStateException("Wallet not found for user: " + reservation.getUser().getId()));

        wallet.settleCredits(reservation.getReservedCredits());
        creditWalletRepository.save(wallet);

        reservation.settle(reservation.getReservedCredits(), Instant.now());
        aiCreditReservationRepository.save(reservation);

        CreditLedgerEntry entry = CreditLedgerEntry.create(
                wallet,
                reservation.getUser(),
                LedgerEntryType.USAGE,
                0L,
                -reservation.getReservedCredits(),
                wallet.getAvailableCredits(),
                wallet.getReservedCredits(),
                LedgerReferenceType.AI_EXECUTION,
                aiExecutionId,
                "USAGE:AI_EXECUTION:" + aiExecutionId,
                "Quyết toán credit cho thao tác AI: " + reservation.getPurpose());
        creditLedgerEntryRepository.save(entry);
    }

    @Override
    @Transactional
    public void releaseReservation(UUID aiExecutionId) {
        var reservationOpt = aiCreditReservationRepository.findWithLockByAiExecutionId(aiExecutionId);
        if (reservationOpt.isEmpty()) {
            return;
        }

        AiCreditReservation reservation = reservationOpt.get();
        if (reservation.getStatus() != CreditReservationStatus.RESERVED) {
            return;
        }

        CreditWallet wallet = creditWalletRepository
                .findByUserIdForUpdate(reservation.getUser().getId())
                .orElseThrow(() -> new IllegalStateException("Wallet not found for user: " + reservation.getUser().getId()));

        wallet.releaseCredits(reservation.getReservedCredits());
        creditWalletRepository.save(wallet);

        reservation.release(Instant.now());
        aiCreditReservationRepository.save(reservation);

        CreditLedgerEntry entry = CreditLedgerEntry.create(
                wallet,
                reservation.getUser(),
                LedgerEntryType.RELEASE_RESERVE,
                reservation.getReservedCredits(),
                -reservation.getReservedCredits(),
                wallet.getAvailableCredits(),
                wallet.getReservedCredits(),
                LedgerReferenceType.AI_EXECUTION,
                aiExecutionId,
                "RELEASE:AI_EXECUTION:" + aiExecutionId,
                "Hoàn lại credit giữ chỗ do thao tác AI: " + reservation.getPurpose());
        creditLedgerEntryRepository.save(entry);
    }

    private AiCreditRate findRateOrThrow(AiPurpose purpose, String modelCategory) {
        if (modelCategory != null && !modelCategory.isBlank()) {
            var rate = aiCreditRateRepository
                    .findFirstByPurposeAndModelCategoryAndStatusOrderByEffectiveFromDesc(
                            purpose, modelCategory, AiCreditRateStatus.ACTIVE);
            if (rate.isPresent()) {
                return rate.get();
            }
        }
        return aiCreditRateRepository
                .findFirstByPurposeAndStatusOrderByEffectiveFromDesc(
                        purpose, AiCreditRateStatus.ACTIVE)
                .or(() -> aiCreditRateRepository
                        .findFirstByPurposeAndModelCategoryAndStatusOrderByEffectiveFromDesc(
                                purpose, null, AiCreditRateStatus.ACTIVE))
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Chưa có giá AI Credit đang hoạt động cho mục đích: " + purpose));
    }
}
