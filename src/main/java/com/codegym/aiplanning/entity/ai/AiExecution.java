package com.codegym.aiplanning.entity.ai;

import com.codegym.aiplanning.common.entity.BaseEntity;
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
@Table(name = "ai_executions")
public class AiExecution extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserAccount owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_config_id", nullable = false)
    private AiProviderConfig providerConfig;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AiPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AiExecutionOperation operation;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 40)
    private AiExecutionTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "active_slot_target_id")
    private UUID activeSlotTargetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AiExecutionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_type", length = 40)
    private AiExecutionResultType resultType;

    @Column(name = "result_id")
    private UUID resultId;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    protected AiExecution() {}

    public static AiExecution queue(
            UserAccount owner,
            AiProviderConfig providerConfig,
            AiPurpose purpose,
            AiExecutionOperation operation,
            AiExecutionTargetType targetType,
            UUID targetId,
            String idempotencyKey) {
        AiExecution execution = new AiExecution();
        execution.owner = owner;
        execution.providerConfig = providerConfig;
        execution.purpose = purpose;
        execution.operation = operation;
        execution.targetType = targetType;
        execution.targetId = targetId;
        execution.activeSlotTargetId = targetId;
        execution.status = AiExecutionStatus.QUEUED;
        execution.idempotencyKey = idempotencyKey;
        execution.attemptCount = 0;
        return execution;
    }

    public void markSucceeded(
            AiExecutionResultType resultType, UUID resultId, Instant completedAt) {
        requireRunning();
        this.status = AiExecutionStatus.SUCCEEDED;
        this.resultType = resultType;
        this.resultId = resultId;
        this.failureCode = null;
        this.failureMessage = null;
        this.completedAt = completedAt;
        this.leaseExpiresAt = null;
        this.activeSlotTargetId = null;
    }

    public void markFailed(String failureCode, String failureMessage, Instant completedAt) {
        requireRunning();
        this.status = AiExecutionStatus.FAILED;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.completedAt = completedAt;
        this.leaseExpiresAt = null;
        this.activeSlotTargetId = null;
    }

    public boolean isRunning() {
        return status == AiExecutionStatus.RUNNING;
    }

    private void requireRunning() {
        if (!isRunning()) {
            throw new IllegalStateException("Only a RUNNING AI execution may complete.");
        }
    }

    public UserAccount getOwner() {
        return owner;
    }

    public AiProviderConfig getProviderConfig() {
        return providerConfig;
    }

    public AiPurpose getPurpose() {
        return purpose;
    }

    public AiExecutionOperation getOperation() {
        return operation;
    }

    public AiExecutionTargetType getTargetType() {
        return targetType;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public AiExecutionStatus getStatus() {
        return status;
    }

    public AiExecutionResultType getResultType() {
        return resultType;
    }

    public UUID getResultId() {
        return resultId;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
