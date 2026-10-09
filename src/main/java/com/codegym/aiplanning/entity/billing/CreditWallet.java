package com.codegym.aiplanning.entity.billing;

import com.codegym.aiplanning.common.entity.BaseEntity;
import com.codegym.aiplanning.entity.auth.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "credit_wallets")
public class CreditWallet extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserAccount user;

    @Column(name = "available_credits", nullable = false)
    private long availableCredits;

    @Column(name = "reserved_credits", nullable = false)
    private long reservedCredits;

    protected CreditWallet() {}

    public static CreditWallet create(UserAccount user) {
        CreditWallet wallet = new CreditWallet();
        wallet.user = user;
        wallet.availableCredits = 0L;
        wallet.reservedCredits = 0L;
        return wallet;
    }

    public static CreditWallet createWithBalances(UserAccount user, long availableCredits, long reservedCredits) {
        CreditWallet wallet = new CreditWallet();
        wallet.user = user;
        wallet.availableCredits = availableCredits;
        wallet.reservedCredits = reservedCredits;
        return wallet;
    }

    public UserAccount getUser() {
        return user;
    }

    public long getAvailableCredits() {
        return availableCredits;
    }

    public long getReservedCredits() {
        return reservedCredits;
    }

    public void updateProjection(long availableCredits, long reservedCredits) {
        this.availableCredits = availableCredits;
        this.reservedCredits = reservedCredits;
    }

    public void reserveCredits(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Reservation amount must be positive");
        }
        if (this.availableCredits < amount) {
            throw new IllegalStateException("Insufficient available credits for reservation");
        }
        this.availableCredits -= amount;
        this.reservedCredits += amount;
    }

    public void settleCredits(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Settlement amount must be positive");
        }
        if (this.reservedCredits < amount) {
            throw new IllegalStateException("Cannot settle more than reserved credits");
        }
        this.reservedCredits -= amount;
    }

    public void releaseCredits(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Release amount must be positive");
        }
        if (this.reservedCredits < amount) {
            throw new IllegalStateException("Cannot release more than reserved credits");
        }
        this.reservedCredits -= amount;
        this.availableCredits += amount;
    }
}
