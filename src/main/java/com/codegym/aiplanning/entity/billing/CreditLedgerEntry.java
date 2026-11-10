package com.codegym.aiplanning.entity.billing;

import com.codegym.aiplanning.entity.auth.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "credit_ledger_entries")
public class CreditLedgerEntry {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    private CreditWallet wallet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 50)
    private LedgerEntryType entryType;

    @Column(name = "available_delta", nullable = false)
    private long availableDelta;

    @Column(name = "reserved_delta", nullable = false)
    private long reservedDelta;

    @Column(name = "available_balance_after", nullable = false)
    private long availableBalanceAfter;

    @Column(name = "reserved_balance_after", nullable = false)
    private long reservedBalanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 50)
    private LedgerReferenceType referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "idempotency_key", length = 150, unique = true)
    private String idempotencyKey;

    @Column(length = 255)
    private String description;

    @Column(name = "top_up_order_id", unique = true)
    private UUID topUpOrderId;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected CreditLedgerEntry() {}

    public static CreditLedgerEntry create(
            CreditWallet wallet,
            UserAccount user,
            LedgerEntryType entryType,
            long availableDelta,
            long reservedDelta,
            long availableBalanceAfter,
            long reservedBalanceAfter,
            LedgerReferenceType referenceType,
            UUID referenceId,
            String idempotencyKey,
            String description) {
        CreditLedgerEntry entry = new CreditLedgerEntry();
        entry.wallet = wallet;
        entry.user = user;
        entry.entryType = entryType;
        entry.availableDelta = availableDelta;
        entry.reservedDelta = reservedDelta;
        entry.availableBalanceAfter = availableBalanceAfter;
        entry.reservedBalanceAfter = reservedBalanceAfter;
        entry.referenceType = referenceType;
        entry.referenceId = referenceId;
        if (entryType == LedgerEntryType.TOP_UP && referenceType == LedgerReferenceType.ORDER) {
            entry.topUpOrderId = referenceId;
        }
        entry.idempotencyKey = idempotencyKey;
        entry.description = description;
        entry.recordedAt = Instant.now();
        return entry;
    }

    public UUID getId() {
        return id;
    }

    public CreditWallet getWallet() {
        return wallet;
    }

    public UserAccount getUser() {
        return user;
    }

    public LedgerEntryType getEntryType() {
        return entryType;
    }

    public long getAvailableDelta() {
        return availableDelta;
    }

    public long getReservedDelta() {
        return reservedDelta;
    }

    public long getAvailableBalanceAfter() {
        return availableBalanceAfter;
    }

    public long getReservedBalanceAfter() {
        return reservedBalanceAfter;
    }

    public LedgerReferenceType getReferenceType() {
        return referenceType;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getDescription() {
        return description;
    }

    public UUID getTopUpOrderId() {
        return topUpOrderId;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
