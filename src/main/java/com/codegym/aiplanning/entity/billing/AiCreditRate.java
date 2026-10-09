package com.codegym.aiplanning.entity.billing;

import com.codegym.aiplanning.common.entity.BaseEntity;
import com.codegym.aiplanning.entity.ai.AiPurpose;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "ai_credit_rates")
public class AiCreditRate extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AiPurpose purpose;

    @Column(name = "model_category", length = 50)
    private String modelCategory;

    @Column(name = "credit_cost", nullable = false)
    private long creditCost;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiCreditRateStatus status;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    protected AiCreditRate() {}

    public static AiCreditRate create(
            AiPurpose purpose,
            String modelCategory,
            long creditCost,
            AiCreditRateStatus status,
            Instant effectiveFrom) {
        if (creditCost <= 0) {
            throw new IllegalArgumentException("Credit cost must be positive");
        }
        AiCreditRate rate = new AiCreditRate();
        rate.purpose = purpose;
        rate.modelCategory = modelCategory;
        rate.creditCost = creditCost;
        rate.status = status;
        rate.effectiveFrom = effectiveFrom != null ? effectiveFrom : Instant.now();
        return rate;
    }

    public AiPurpose getPurpose() {
        return purpose;
    }

    public String getModelCategory() {
        return modelCategory;
    }

    public long getCreditCost() {
        return creditCost;
    }

    public AiCreditRateStatus getStatus() {
        return status;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setStatus(AiCreditRateStatus status) {
        this.status = status;
    }

    public void setCreditCost(long creditCost) {
        if (creditCost <= 0) {
            throw new IllegalArgumentException("Credit cost must be positive");
        }
        this.creditCost = creditCost;
    }
}
