package com.codegym.aiplanning.entity.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_execution_inputs")
public class AiExecutionInput {

    @Id
    @Column(name = "execution_id", nullable = false)
    private UUID executionId;

    @Column(name = "adjustment_prompt", length = 1000)
    private String adjustmentPrompt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AiExecutionInput() {}

    public static AiExecutionInput create(
            UUID executionId, String adjustmentPrompt, Instant createdAt) {
        AiExecutionInput input = new AiExecutionInput();
        input.executionId = executionId;
        input.adjustmentPrompt = adjustmentPrompt;
        input.createdAt = createdAt;
        return input;
    }

    public String getAdjustmentPrompt() {
        return adjustmentPrompt;
    }
}
