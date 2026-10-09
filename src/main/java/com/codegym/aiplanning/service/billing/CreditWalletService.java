package com.codegym.aiplanning.service.billing;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.billing.dto.CreditWalletResponse;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.billing.CreditWallet;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.repository.billing.CreditWalletRepository;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.codegym.aiplanning.controller.billing.dto.CreditTransactionResponse;
import com.codegym.aiplanning.entity.billing.LedgerEntryType;
import com.codegym.aiplanning.repository.billing.CreditLedgerEntryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
public class CreditWalletService {

    private static final Logger log = LoggerFactory.getLogger(CreditWalletService.class);

    private final CreditWalletRepository creditWalletRepository;
    private final UserAccountRepository userAccountRepository;
    private final CreditWalletAtomicInitializer atomicInitializer;
    private final CreditLedgerEntryRepository creditLedgerEntryRepository;

    public CreditWalletService(
            CreditWalletRepository creditWalletRepository,
            UserAccountRepository userAccountRepository,
            CreditWalletAtomicInitializer atomicInitializer,
            CreditLedgerEntryRepository creditLedgerEntryRepository) {
        this.creditWalletRepository = creditWalletRepository;
        this.userAccountRepository = userAccountRepository;
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
        return creditLedgerEntryRepository
                .findFiltered(userId, entryType, fromDate, toDate, pageable)
                .map(CreditTransactionResponse::from);
    }

    public CreditWalletResponse getOrCreateWallet(UUID userId) {
        Optional<CreditWallet> existingWallet = creditWalletRepository.findByUserId(userId);
        if (existingWallet.isPresent()) {
            return toResponse(existingWallet.get());
        }

        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "User account not found"));

        try {
            CreditWallet createdWallet = atomicInitializer.createWalletWithWelcomeBonus(user);
            return toResponse(createdWallet);
        } catch (DataIntegrityViolationException ex) {
            if (!isExpectedUniqueConstraintViolation(ex)) {
                throw ex;
            }
            log.info("Detected concurrent wallet initialization race for user {}. Re-fetching created wallet.", userId);
            // Race condition occurred; another concurrent request already created the wallet.
            // Transaction A has rolled back cleanly. Now re-fetch the existing wallet in a new read operation.
            return creditWalletRepository.findByUserId(userId)
                    .map(this::toResponse)
                    .orElseThrow(() -> ex);
        }
    }

    private boolean isExpectedUniqueConstraintViolation(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException cve) {
                String sqlState = cve.getSQLState();
                if ("23505".equals(sqlState)) {
                    return true;
                }
                String constraintName = cve.getConstraintName();
                if (constraintName != null) {
                    String lower = constraintName.toLowerCase(Locale.ROOT);
                    if (lower.contains("uk_credit_wallets_user")
                            || lower.contains("uk_credit_ledger_entries_idempotency_key")) {
                        return true;
                    }
                }
            }
            if (cause instanceof java.sql.SQLException sqlEx) {
                if ("23505".equals(sqlEx.getSQLState())) {
                    return true;
                }
            }
            String msg = cause.getMessage();
            if (msg != null) {
                String lowerMsg = msg.toLowerCase(Locale.ROOT);
                if (lowerMsg.contains("uk_credit_wallets_user")
                        || lowerMsg.contains("uk_credit_ledger_entries_idempotency_key")
                        || lowerMsg.contains("23505")
                        || lowerMsg.contains("duplicate key")
                        || lowerMsg.contains("unique index or primary key violation")) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }

    private CreditWalletResponse toResponse(CreditWallet wallet) {
        return new CreditWalletResponse(
                wallet.getId(),
                wallet.getAvailableCredits(),
                wallet.getReservedCredits());
    }
}
