package com.codegym.aiplanning.repository.billing;

import com.codegym.aiplanning.entity.billing.CreditLedgerEntry;
import com.codegym.aiplanning.entity.billing.LedgerEntryType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditLedgerEntryRepository extends JpaRepository<CreditLedgerEntry, UUID> {

    List<CreditLedgerEntry> findByWalletIdOrderByRecordedAtDesc(UUID walletId);

    List<CreditLedgerEntry> findByUserIdOrderByRecordedAtDesc(UUID userId);

    @org.springframework.data.jpa.repository.Query("SELECT e FROM CreditLedgerEntry e WHERE e.user.id = :userId "
            + "AND (:entryType IS NULL OR e.entryType = :entryType) "
            + "AND (:fromDate IS NULL OR e.recordedAt >= :fromDate) "
            + "AND (:toDate IS NULL OR e.recordedAt <= :toDate)")
    org.springframework.data.domain.Page<CreditLedgerEntry> findFiltered(
            @org.springframework.data.repository.query.Param("userId") UUID userId,
            @org.springframework.data.repository.query.Param("entryType") LedgerEntryType entryType,
            @org.springframework.data.repository.query.Param("fromDate") java.time.Instant fromDate,
            @org.springframework.data.repository.query.Param("toDate") java.time.Instant toDate,
            org.springframework.data.domain.Pageable pageable);
}
