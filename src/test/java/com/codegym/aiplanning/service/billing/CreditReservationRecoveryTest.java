package com.codegym.aiplanning.service.billing;

import static org.mockito.Mockito.*;
import com.codegym.aiplanning.entity.ai.AiExecution;
import com.codegym.aiplanning.entity.ai.AiExecutionStatus;
import com.codegym.aiplanning.repository.ai.AiExecutionRepository;
import com.codegym.aiplanning.repository.billing.AiCreditReservationRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class CreditReservationRecoveryTest {
    @Test
    void settlesSuccessReleasesFailureAndNeverRefundsLiveWork() {
        var reservations = mock(AiCreditReservationRepository.class);
        var executions = mock(AiExecutionRepository.class);
        var credits = mock(CreditReservationService.class);
        var transactions = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            java.util.function.Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactions).executeWithoutResult(any());
        var ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(reservations.findTerminalUnsettledExecutions(any())).thenReturn(ids);
        var statuses = List.of(AiExecutionStatus.SUCCEEDED, AiExecutionStatus.FAILED,
                AiExecutionStatus.RUNNING, AiExecutionStatus.QUEUED);
        for (int i = 0; i < ids.size(); i++) {
            var execution = mock(AiExecution.class);
            when(execution.getStatus()).thenReturn(statuses.get(i));
            when(executions.findByIdForUpdate(ids.get(i))).thenReturn(Optional.of(execution));
        }
        new CreditReservationRecovery(reservations, executions, credits, transactions).reconcileTerminalExecutions();
        verify(credits).settleReservation(ids.get(0));
        verify(credits).releaseReservation(ids.get(1));
        verifyNoMoreInteractions(credits);
    }
}
