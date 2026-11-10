package com.codegym.aiplanning.service.ai.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.admin.ai.dto.AdminAiExecutionDetailResponse;
import com.codegym.aiplanning.controller.admin.ai.dto.AdminAiExecutionFilter;
import com.codegym.aiplanning.controller.admin.ai.dto.AdminAiExecutionListResponse;
import com.codegym.aiplanning.controller.admin.ai.dto.AdminAiExecutionTimelineEvent;
import com.codegym.aiplanning.entity.ai.AiExecution;
import com.codegym.aiplanning.entity.ai.AiExecutionOperation;
import com.codegym.aiplanning.entity.ai.AiExecutionStatus;
import com.codegym.aiplanning.entity.ai.AiPurpose;
import com.codegym.aiplanning.repository.ai.AiExecutionRepository;
import com.codegym.aiplanning.service.ai.AdminAiExecutionService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminAiExecutionServiceImpl implements AdminAiExecutionService {

    private final AiExecutionRepository executionRepository;

    public AdminAiExecutionServiceImpl(AiExecutionRepository executionRepository) {
        this.executionRepository = executionRepository;
    }

    @Override
    public Page<AdminAiExecutionListResponse> listExecutions(AdminAiExecutionFilter filter, Pageable pageable) {
        validateFilter(filter);
        Page<AiExecution> page = executionRepository.searchAdminExecutions(filter, pageable);
        return page.map(this::toListResponse);
    }

    @Override
    public AdminAiExecutionDetailResponse getExecutionDetail(UUID executionId) {
        if (executionId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Execution ID must not be null.");
        }

        AiExecution execution = executionRepository.findAdminDetailById(executionId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.AI_EXECUTION_NOT_FOUND,
                        "AI execution not found: " + executionId));

        return toDetailResponse(execution);
    }

    private void validateFilter(AdminAiExecutionFilter filter) {
        if (filter == null) {
            return;
        }

        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "The 'from' timestamp must not be after 'to'.");
        }

        if (filter.purpose() != null && !filter.purpose().isBlank()) {
            try {
                AiPurpose.valueOf(filter.purpose().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_FAILED,
                        "Invalid purpose: " + filter.purpose());
            }
        }

        if (filter.operation() != null && !filter.operation().isBlank()) {
            try {
                AiExecutionOperation.valueOf(filter.operation().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_FAILED,
                        "Invalid operation: " + filter.operation());
            }
        }

        if (filter.status() != null && !filter.status().isBlank()) {
            try {
                AiExecutionStatus.valueOf(filter.status().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_FAILED,
                        "Invalid status: " + filter.status());
            }
        }
    }

    private AdminAiExecutionListResponse toListResponse(AiExecution execution) {
        UUID providerId = null;
        String providerName = null;
        String model = null;

        if (execution.getProviderConfig() != null) {
            model = execution.getProviderConfig().getModel();
            if (execution.getProviderConfig().getProvider() != null) {
                providerId = execution.getProviderConfig().getProvider().getId();
                providerName = execution.getProviderConfig().getProvider().getDisplayName();
            }
        }

        return new AdminAiExecutionListResponse(
                execution.getId(),
                providerId,
                providerName,
                model,
                execution.getPurpose(),
                execution.getOperation(),
                execution.getStatus(),
                execution.getFailureCode(),
                execution.getCreatedAt()
        );
    }

    private AdminAiExecutionDetailResponse toDetailResponse(AiExecution execution) {
        UUID providerId = null;
        String providerName = null;
        String model = null;

        if (execution.getProviderConfig() != null) {
            model = execution.getProviderConfig().getModel();
            if (execution.getProviderConfig().getProvider() != null) {
                providerId = execution.getProviderConfig().getProvider().getId();
                providerName = execution.getProviderConfig().getProvider().getDisplayName();
            }
        }

        List<AdminAiExecutionTimelineEvent> timeline = buildTimeline(execution);

        return new AdminAiExecutionDetailResponse(
                execution.getId(),
                providerId,
                providerName,
                model,
                execution.getPurpose(),
                execution.getOperation(),
                execution.getStatus(),
                execution.getAttemptCount(),
                execution.getCreatedAt(),
                execution.getStartedAt(),
                execution.getCompletedAt(),
                execution.getLatencyMs(),
                execution.getInputTokens(),
                execution.getOutputTokens(),
                execution.getFailureCode(),
                execution.getFailureMessage(),
                timeline
        );
    }

    private List<AdminAiExecutionTimelineEvent> buildTimeline(AiExecution execution) {
        List<AdminAiExecutionTimelineEvent> timeline = new ArrayList<>();

        // 1. QUEUED milestone
        timeline.add(new AdminAiExecutionTimelineEvent("QUEUED", execution.getCreatedAt(), false));

        // 2. RUNNING milestone
        if (execution.getStartedAt() != null) {
            timeline.add(new AdminAiExecutionTimelineEvent("RUNNING", execution.getStartedAt(), false));
        }

        // 3. RETRY milestones (synthetic = true since exact retry timestamps are not persisted)
        if (execution.getAttemptCount() > 1) {
            for (int attempt = 2; attempt <= execution.getAttemptCount(); attempt++) {
                timeline.add(new AdminAiExecutionTimelineEvent(
                        "RETRY (Attempt " + attempt + ")",
                        null,
                        true
                ));
            }
        }

        // 4. Terminal status
        AiExecutionStatus status = execution.getStatus();
        if (status == AiExecutionStatus.SUCCEEDED
                || status == AiExecutionStatus.FAILED
                || status == AiExecutionStatus.TIMEOUT) {
            timeline.add(new AdminAiExecutionTimelineEvent(status.name(), execution.getCompletedAt(), false));
        }

        return timeline;
    }
}
