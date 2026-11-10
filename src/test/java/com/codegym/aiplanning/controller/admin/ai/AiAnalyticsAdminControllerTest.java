package com.codegym.aiplanning.controller.admin.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.common.api.ApiResponse;
import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.admin.ai.dto.AiExecutionAnalyticsResponse;
import com.codegym.aiplanning.entity.ai.AiExecutionOperation;
import com.codegym.aiplanning.service.ai.AiAnalyticsAdminService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiAnalyticsAdminControllerTest {

    private AiAnalyticsAdminService analyticsService;
    private AiAnalyticsAdminController controller;

    @BeforeEach
    void setUp() {
        analyticsService = mock(AiAnalyticsAdminService.class);
        controller = new AiAnalyticsAdminController(analyticsService);
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

        when(analyticsService.getAnalytics(from, to))
                .thenReturn(List.of(item));

        ApiResponse<List<AiExecutionAnalyticsResponse>> response =
                controller.getAnalytics(from, to);

        assertThat(response.data()).hasSize(1);
        AiExecutionAnalyticsResponse result = response.data().get(0);
        assertThat(result.providerDisplayName()).isEqualTo("OpenAI");
        assertThat(result.model()).isEqualTo("gpt-4o");
        assertThat(result.operation()).isEqualTo(AiExecutionOperation.GENERATE);
        assertThat(result.totalExecutions()).isEqualTo(10L);
        assertThat(result.succeededCount()).isEqualTo(8L);
        assertThat(result.failedCount()).isEqualTo(1L);
        assertThat(result.timeoutCount()).isEqualTo(1L);
        assertThat(result.avgLatencyMs()).isEqualTo(1250.5);
        assertThat(result.totalInputTokens()).isEqualTo(5000L);
        assertThat(result.totalOutputTokens()).isEqualTo(2000L);
        assertThat(result.totalTokens()).isEqualTo(7000L);

        verify(analyticsService).getAnalytics(from, to);
    }

    @Test
    void getAnalytics_whenServiceThrowsException_shouldPropagate() {
        Instant now = Instant.now();
        Instant from = now;
        Instant to = now.minus(1, ChronoUnit.DAYS);

        when(analyticsService.getAnalytics(from, to))
                .thenThrow(new BusinessException(
                        ErrorCode.VALIDATION_FAILED,
                        "The 'from' timestamp must not be after 'to'."));

        assertThatThrownBy(() -> controller.getAnalytics(from, to))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                });

        verify(analyticsService).getAnalytics(from, to);
    }

    @Test
    void getAnalytics_whenParametersAreNull_shouldDelegateToService() {
        when(analyticsService.getAnalytics(null, null))
                .thenReturn(List.of());

        ApiResponse<List<AiExecutionAnalyticsResponse>> response =
                controller.getAnalytics(null, null);

        assertThat(response.data()).isEmpty();
        verify(analyticsService).getAnalytics(null, null);
    }
}
