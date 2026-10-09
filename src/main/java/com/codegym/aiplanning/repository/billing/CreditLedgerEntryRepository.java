package com.codegym.aiplanning.repository.billing;

import com.codegym.aiplanning.entity.billing.CreditLedgerEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditLedgerEntryRepository extends JpaRepository<CreditLedgerEntry, UUID> {

    List<CreditLedgerEntry> findByWalletIdOrderByRecordedAtDesc(UUID walletId);

    List<CreditLedgerEntry> findByUserIdOrderByRecordedAtDesc(UUID userId);
}
