package com.codegym.aiplanning.service.ai.execution;

import com.codegym.aiplanning.entity.ai.AiExecution;
import com.codegym.aiplanning.entity.ai.AiExecutionStatus;
import com.codegym.aiplanning.repository.ai.AiExecutionRepository;
import java.time.Instant;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AiExecutionDispatcher {

    private final AiExecutionRepository executionRepository;
    private final AiExecutionWorker worker;

    public AiExecutionDispatcher(
            AiExecutionRepository executionRepository, AiExecutionWorker worker) {
        this.executionRepository = executionRepository;
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${app.ai.execution.dispatch-interval-ms:1000}")
    @Transactional
    public void dispatchQueuedExecutions() {
        executionRepository.requeueExpired(Instant.now());
        for (AiExecution execution : executionRepository
                .findTop25ByStatusOrderByCreatedAtAsc(AiExecutionStatus.QUEUED)) {
            try {
                worker.executeAsync(execution.getId());
            } catch (TaskRejectedException exception) {
                // All workers are occupied. This row stays QUEUED for the next cycle.
                break;
            }
        }
    }
}
