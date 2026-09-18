package com.codegym.aiplanning.service.ai.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.admin.ai.dto.AiExecutionAnalyticsResponse;
import com.codegym.aiplanning.repository.ai.AiExecutionRepository;
import com.codegym.aiplanning.service.ai.AiAnalyticsAdminService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AiAnalyticsAdminServiceImpl implements AiAnalyticsAdminService {

    private static final long MAX_TIME_WINDOW_DAYS = 90;

    private final AiExecutionRepository executionRepository;

    public AiAnalyticsAdminServiceImpl(AiExecutionRepository executionRepository) {
        this.executionRepository = executionRepository;
    }

    @Override
    public List<AiExecutionAnalyticsResponse> getAnalytics(Instant from, Instant to) {
        Instant effectiveTo = to != null ? to : Instant.now();
        Instant effectiveFrom = from != null ? from : effectiveTo.minus(Duration.ofDays(7));

        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "The 'from' timestamp must not be after 'to'.");
        }

        if (Duration.between(effectiveFrom, effectiveTo).toDays() > MAX_TIME_WINDOW_DAYS) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "The requested analytics time window cannot exceed " + MAX_TIME_WINDOW_DAYS + " days.");
        }

        return executionRepository.aggregateAnalytics(effectiveFrom, effectiveTo);
    }
}
