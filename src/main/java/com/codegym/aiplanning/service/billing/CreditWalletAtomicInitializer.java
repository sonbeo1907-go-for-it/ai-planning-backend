package com.codegym.aiplanning.service.billing;

import com.codegym.aiplanning.config.BillingProperties;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.billing.CreditLedgerEntry;
import com.codegym.aiplanning.entity.billing.CreditWallet;
import com.codegym.aiplanning.entity.billing.LedgerEntryType;
import com.codegym.aiplanning.entity.billing.LedgerReferenceType;
import com.codegym.aiplanning.repository.billing.CreditLedgerEntryRepository;
import com.codegym.aiplanning.repository.billing.CreditWalletRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CreditWalletAtomicInitializer {

    private final CreditWalletRepository creditWalletRepository;
    private final CreditLedgerEntryRepository creditLedgerEntryRepository;
    private final BillingProperties billingProperties;

    public CreditWalletAtomicInitializer(
            CreditWalletRepository creditWalletRepository,
            CreditLedgerEntryRepository creditLedgerEntryRepository,
            BillingProperties billingProperties) {
        this.creditWalletRepository = creditWalletRepository;
        this.creditLedgerEntryRepository = creditLedgerEntryRepository;
        this.billingProperties = billingProperties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CreditWallet createWalletWithWelcomeBonus(UserAccount user) {
        CreditWallet wallet = CreditWallet.create(user);
        wallet = creditWalletRepository.save(wallet);

        long welcomeCredits = billingProperties.welcomeCredits();
        if (welcomeCredits > 0) {
            long currentAvailable = wallet.getAvailableCredits();
            long availableDelta = welcomeCredits;
            long availableBalanceAfter = currentAvailable + availableDelta;
            long reservedDelta = 0L;
            long reservedBalanceAfter = wallet.getReservedCredits();
            String idempotencyKey = "WELCOME_BONUS:" + user.getId();

            CreditLedgerEntry ledgerEntry = CreditLedgerEntry.create(
                    wallet,
                    user,
                    LedgerEntryType.WELCOME_BONUS,
                    availableDelta,
                    reservedDelta,
                    availableBalanceAfter,
                    reservedBalanceAfter,
                    LedgerReferenceType.ACCOUNT,
                    user.getId(),
                    idempotencyKey,
                    "Welcome bonus credits");
            creditLedgerEntryRepository.save(ledgerEntry);

            wallet.updateProjection(availableBalanceAfter, reservedBalanceAfter);
            wallet = creditWalletRepository.save(wallet);
        }

        creditWalletRepository.flush();
        creditLedgerEntryRepository.flush();
        return wallet;
    }
}
