package com.codegym.aiplanning.service.ai.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.admin.ai.dto.AiExecutionAnalyticsResponse;
import com.codegym.aiplanning.entity.ai.AiExecutionOperation;
import com.codegym.aiplanning.repository.ai.AiExecutionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiAnalyticsAdminServiceImplTest {

    private AiExecutionRepository executionRepository;
    private AiAnalyticsAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        executionRepository = mock(AiExecutionRepository.class);
        service = new AiAnalyticsAdminServiceImpl(executionRepository);
    }

    @Test
    void getAnalytics_shouldReturnAggregatedResults() {
        Instant now = Instant.now();
        Instant from = now.minus(7, ChronoUnit.DAYS);
        Instant to = now;

        AiExecutionAnalyticsResponse item = new AiExecutionAnalyticsResponse(
                "OpenAI",
                "gpt-4o",
                AiExecutionOperation.GENERATE,
                10L,
                8L,
                1L,
                1L,
                1250.5,
                5000L,
                2000L
        );

        when(executionRepository.aggregateAnalytics(from, to))
                .thenReturn(List.of(item));

        List<AiExecutionAnalyticsResponse> result = service.getAnalytics(from, to);

        assertThat(result).hasSize(1);
        AiExecutionAnalyticsResponse analytics = result.get(0);
        assertThat(analytics.providerDisplayName()).isEqualTo("OpenAI");
        assertThat(analytics.model()).isEqualTo("gpt-4o");
        assertThat(analytics.operation()).isEqualTo(AiExecutionOperation.GENERATE);
        assertThat(analytics.totalExecutions()).isEqualTo(10L);
        assertThat(analytics.succeededCount()).isEqualTo(8L);
        assertThat(analytics.failedCount()).isEqualTo(1L);
        assertThat(analytics.timeoutCount()).isEqualTo(1L);
        assertThat(analytics.avgLatencyMs()).isEqualTo(1250.5);
        assertThat(analytics.totalInputTokens()).isEqualTo(5000L);
        assertThat(analytics.totalOutputTokens()).isEqualTo(2000L);
        assertThat(analytics.totalTokens()).isEqualTo(7000L);

        verify(executionRepository).aggregateAnalytics(from, to);
    }

    @Test
    void getAnalytics_whenParametersAreNull_shouldDefaultToLast7Days() {
        when(executionRepository.aggregateAnalytics(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        List<AiExecutionAnalyticsResponse> result = service.getAnalytics(null, null);

        assertThat(result).isEmpty();

        ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> toCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(executionRepository).aggregateAnalytics(fromCaptor.capture(), toCaptor.capture());

        Instant capturedFrom = fromCaptor.getValue();
        Instant capturedTo = toCaptor.getValue();
        assertThat(capturedFrom).isBefore(capturedTo);
        long daysDiff = ChronoUnit.DAYS.between(capturedFrom, capturedTo);
        assertThat(daysDiff).isEqualTo(7L);
    }

    @Test
    void getAnalytics_whenFromIsAfterTo_shouldThrowValidationException() {
        Instant now = Instant.now();
        Instant from = now;
        Instant to = now.minus(1, ChronoUnit.DAYS);

        assertThatThrownBy(() -> service.getAnalytics(from, to))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(be.getMessage()).contains("must not be after");
                });
    }

    @Test
    void getAnalytics_whenRangeExceeds90Days_shouldThrowValidationException() {
        Instant now = Instant.now();
        Instant to = now;
        Instant from = now.minus(95, ChronoUnit.DAYS);

        assertThatThrownBy(() -> service.getAnalytics(from, to))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(be.getMessage()).contains("cannot exceed 90 days");
                });
    }
}
