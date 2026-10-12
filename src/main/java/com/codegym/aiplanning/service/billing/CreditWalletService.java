package com.codegym.aiplanning.service.billing;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.billing.dto.CreditWalletResponse;
import com.codegym.aiplanning.entity.billing.CreditWallet;
import com.codegym.aiplanning.repository.billing.CreditWalletRepository;
import java.util.UUID;
import com.codegym.aiplanning.controller.billing.dto.CreditTransactionResponse;
import com.codegym.aiplanning.entity.billing.LedgerEntryType;
import com.codegym.aiplanning.repository.billing.CreditLedgerEntryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
public class CreditWalletService {

    private final CreditWalletRepository creditWalletRepository;
    private final CreditWalletAtomicInitializer atomicInitializer;
    private final CreditLedgerEntryRepository creditLedgerEntryRepository;

    public CreditWalletService(
            CreditWalletRepository creditWalletRepository,
            CreditWalletAtomicInitializer atomicInitializer,
            CreditLedgerEntryRepository creditLedgerEntryRepository) {
        this.creditWalletRepository = creditWalletRepository;
        this.atomicInitializer = atomicInitializer;
        this.creditLedgerEntryRepository = creditLedgerEntryRepository;
    }

    @Transactional(readOnly = true)
    public Page<CreditTransactionResponse> getTransactions(
            UUID userId,
            LedgerEntryType entryType,
            Instant fromDate,
            Instant toDate,
            Pageable pageable) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "fromDate must not be after toDate");
        }
        for (var order : pageable.getSort()) {
            if (!java.util.Set.of("recordedAt", "id").contains(order.getProperty())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Unsupported transaction sort field");
            }
        }
        Pageable stablePage = org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100),
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Order.desc("recordedAt"),
                        org.springframework.data.domain.Sort.Order.desc("id")));
        return creditLedgerEntryRepository
                .findFiltered(userId, entryType, fromDate, toDate, stablePage)
                .map(CreditTransactionResponse::from);
    }

    public CreditWalletResponse getOrCreateWallet(UUID userId) {
        return toResponse(atomicInitializer.getOrCreate(userId));
    }

    private CreditWalletResponse toResponse(CreditWallet wallet) {
        return new CreditWalletResponse(
                wallet.getId(),
                wallet.getAvailableCredits(),
                wallet.getReservedCredits());
    }
}
