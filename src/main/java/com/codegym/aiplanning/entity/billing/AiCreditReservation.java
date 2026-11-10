package com.codegym.aiplanning.entity.billing;

import com.codegym.aiplanning.common.entity.BaseEntity;
import com.codegym.aiplanning.entity.ai.AiPurpose;
import com.codegym.aiplanning.entity.auth.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_credit_reservations")
public class AiCreditReservation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    private CreditWallet wallet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(name = "ai_execution_id", nullable = false, unique = true)
    private UUID aiExecutionId;

    @Column(name = "rate_id")
    private UUID rateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AiPurpose purpose;

    @Column(name = "model_category", length = 50)
    private String modelCategory;

    @Column(name = "reserved_credits", nullable = false)
    private long reservedCredits;

    @Column(name = "charged_credits")
    private Long chargedCredits;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CreditReservationStatus status;

    @Column(name = "reserved_at", nullable = false)
    private Instant reservedAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    protected AiCreditReservation() {}

    public static AiCreditReservation reserve(
            CreditWallet wallet,
            UserAccount user,
            UUID aiExecutionId,
            UUID rateId,
            AiPurpose purpose,
            String modelCategory,
            long creditsToReserve,
            Instant reservedAt) {
        if (creditsToReserve <= 0) {
            throw new IllegalArgumentException("Reserved credits must be positive");
        }
        AiCreditReservation reservation = new AiCreditReservation();
        reservation.wallet = wallet;
        reservation.user = user;
        reservation.aiExecutionId = aiExecutionId;
        reservation.rateId = rateId;
        reservation.purpose = purpose;
        reservation.modelCategory = modelCategory;
        reservation.reservedCredits = creditsToReserve;
        reservation.status = CreditReservationStatus.RESERVED;
        reservation.reservedAt = reservedAt != null ? reservedAt : Instant.now();
        return reservation;
    }

    public CreditWallet getWallet() {
        return wallet;
    }

    public UserAccount getUser() {
        return user;
    }

    public UUID getAiExecutionId() {
        return aiExecutionId;
    }

    public UUID getRateId() {
        return rateId;
    }

    public AiPurpose getPurpose() {
        return purpose;
    }

    public String getModelCategory() {
        return modelCategory;
    }

    public long getReservedCredits() {
        return reservedCredits;
    }

    public Long getChargedCredits() {
        return chargedCredits;
    }

    public CreditReservationStatus getStatus() {
        return status;
    }

    public Instant getReservedAt() {
        return reservedAt;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public void settle(long actualChargedCredits, Instant settledAt) {
        if (this.status != CreditReservationStatus.RESERVED) {
            return;
        }
        this.status = CreditReservationStatus.SETTLED;
        this.chargedCredits = actualChargedCredits;
        this.settledAt = settledAt != null ? settledAt : Instant.now();
    }

    public void release(Instant releasedAt) {
        if (this.status != CreditReservationStatus.RESERVED) {
            return;
        }
        this.status = CreditReservationStatus.RELEASED;
        this.releasedAt = releasedAt != null ? releasedAt : Instant.now();
    }
}
