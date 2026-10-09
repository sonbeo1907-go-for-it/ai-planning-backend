package com.codegym.aiplanning.entity.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "billing_abnormal_transactions")
public class BillingAbnormalTransaction {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "order_code", length = 64)
    private String orderCode;

    @Column(name = "external_transaction_id", length = 255)
    private String externalTransactionId;

    @Column(name = "amount_vnd")
    private Long amountVnd;

    @Column(name = "reason", nullable = false, length = 100)
    private String reason;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected BillingAbnormalTransaction() {}

    public BillingAbnormalTransaction(
            String orderCode,
            String externalTransactionId,
            Long amountVnd,
            String reason,
            String details,
            String rawPayload) {
        this.orderCode = orderCode;
        this.externalTransactionId = externalTransactionId;
        this.amountVnd = amountVnd;
        this.reason = reason;
        this.details = details;
        this.rawPayload = rawPayload;
        this.recordedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getOrderCode() {
        return orderCode;
    }

    public String getExternalTransactionId() {
        return externalTransactionId;
    }

    public Long getAmountVnd() {
        return amountVnd;
    }

    public String getReason() {
        return reason;
    }

    public String getDetails() {
        return details;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
