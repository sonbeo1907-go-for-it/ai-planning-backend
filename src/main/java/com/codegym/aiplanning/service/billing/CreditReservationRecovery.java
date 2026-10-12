package com.codegym.aiplanning.service.billing;

import com.codegym.aiplanning.entity.ai.AiExecutionStatus;
import com.codegym.aiplanning.repository.ai.AiExecutionRepository;
import com.codegym.aiplanning.repository.billing.AiCreditReservationRepository;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class CreditReservationRecovery {
    private final AiCreditReservationRepository reservations;
    private final AiExecutionRepository executions;
    private final CreditReservationService credits;
    private final TransactionTemplate transactions;

    public CreditReservationRecovery(AiCreditReservationRepository reservations, AiExecutionRepository executions,
            CreditReservationService credits, TransactionTemplate transactions) {
        this.reservations = reservations;
        this.executions = executions;
        this.credits = credits;
        this.transactions = transactions;
    }

    @Scheduled(fixedDelayString = "${app.billing.reservation-recovery-interval-ms:60000}")
    public void reconcileTerminalExecutions() {
        for (UUID executionId : reservations.findTerminalUnsettledExecutions(PageRequest.of(0, 100))) {
            try {
                transactions.executeWithoutResult(status -> {
                    var execution = executions.findByIdForUpdate(executionId).orElse(null);
                    if (execution == null) return;
                    if (execution.getStatus() == AiExecutionStatus.SUCCEEDED) {
                        credits.settleReservation(executionId);
                    } else if (execution.getStatus() == AiExecutionStatus.FAILED
                            || execution.getStatus() == AiExecutionStatus.TIMEOUT) {
                        credits.releaseReservation(executionId);
                    }
                    // Never refund QUEUED/RUNNING work simply because its reservation is old.
                });
            } catch (RuntimeException exception) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn(
                        "Credit recovery will retry execution {}", executionId);
            }
        }
    }
}
