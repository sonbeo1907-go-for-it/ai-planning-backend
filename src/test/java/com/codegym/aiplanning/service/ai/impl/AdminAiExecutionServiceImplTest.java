package com.codegym.aiplanning.service.ai.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.admin.ai.dto.AdminAiExecutionDetailResponse;
import com.codegym.aiplanning.controller.admin.ai.dto.AdminAiExecutionFilter;
import com.codegym.aiplanning.controller.admin.ai.dto.AdminAiExecutionListResponse;
import com.codegym.aiplanning.controller.admin.ai.dto.AdminAiExecutionTimelineEvent;
import com.codegym.aiplanning.entity.ai.AiExecution;
import com.codegym.aiplanning.entity.ai.AiExecutionOperation;
import com.codegym.aiplanning.entity.ai.AiExecutionStatus;
import com.codegym.aiplanning.entity.ai.AiProvider;
import com.codegym.aiplanning.entity.ai.AiProviderConfig;
import com.codegym.aiplanning.entity.ai.AiPurpose;
import com.codegym.aiplanning.repository.ai.AiExecutionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class AdminAiExecutionServiceImplTest {

    private AiExecutionRepository executionRepository;
    private AdminAiExecutionServiceImpl service;

    @BeforeEach
    void setUp() {
        executionRepository = mock(AiExecutionRepository.class);
        service = new AdminAiExecutionServiceImpl(executionRepository);
    }

    @Nested
    @DisplayName("Filter Validation Tests")
    class FilterValidationTests {

        @Test
        void listExecutions_whenFromAfterTo_throwsValidationFailed() {
            Instant now = Instant.now();
            AdminAiExecutionFilter filter = new AdminAiExecutionFilter(
                    now, now.minus(1, ChronoUnit.HOURS), null, null, null, null, null, null);

            assertThatThrownBy(() -> service.listExecutions(filter, Pageable.unpaged()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).errorCode())
                            .isEqualTo(ErrorCode.VALIDATION_FAILED));
        }

        @Test
        void listExecutions_whenInvalidPurpose_throwsValidationFailed() {
            AdminAiExecutionFilter filter = new AdminAiExecutionFilter(
                    null, null, null, null, "INVALID_PURPOSE", null, null, null);

            assertThatThrownBy(() -> service.listExecutions(filter, Pageable.unpaged()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).errorCode())
                            .isEqualTo(ErrorCode.VALIDATION_FAILED));
        }

        @Test
        void listExecutions_whenInvalidOperation_throwsValidationFailed() {
            AdminAiExecutionFilter filter = new AdminAiExecutionFilter(
                    null, null, null, null, null, "INVALID_OP", null, null);

            assertThatThrownBy(() -> service.listExecutions(filter, Pageable.unpaged()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).errorCode())
                            .isEqualTo(ErrorCode.VALIDATION_FAILED));
        }

        @Test
        void listExecutions_whenInvalidStatus_throwsValidationFailed() {
            AdminAiExecutionFilter filter = new AdminAiExecutionFilter(
                    null, null, null, null, null, null, "NOT_A_STATUS", null);

            assertThatThrownBy(() -> service.listExecutions(filter, Pageable.unpaged()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).errorCode())
                            .isEqualTo(ErrorCode.VALIDATION_FAILED));
        }
    }

    @Nested
    @DisplayName("List Executions Tests")
    class ListExecutionsTests {

        @Test
        void listExecutions_whenValid_mapsToWhitelistedListResponse() {
            UUID executionId = UUID.randomUUID();
            UUID providerId = UUID.randomUUID();
            Instant createdAt = Instant.now();

            AiProvider provider = mock(AiProvider.class);
            when(provider.getId()).thenReturn(providerId);
            when(provider.getDisplayName()).thenReturn("OpenAI");

            AiProviderConfig config = mock(AiProviderConfig.class);
            when(config.getModel()).thenReturn("gpt-4o");
            when(config.getProvider()).thenReturn(provider);

            AiExecution execution = mock(AiExecution.class);
            when(execution.getId()).thenReturn(executionId);
            when(execution.getProviderConfig()).thenReturn(config);
            when(execution.getPurpose()).thenReturn(AiPurpose.ROADMAP_GENERATION);
            when(execution.getOperation()).thenReturn(AiExecutionOperation.GENERATE);
            when(execution.getStatus()).thenReturn(AiExecutionStatus.SUCCEEDED);
            when(execution.getFailureCode()).thenReturn(null);
            when(execution.getCreatedAt()).thenReturn(createdAt);

            Pageable pageable = PageRequest.of(0, 10);
            when(executionRepository.searchAdminExecutions(any(), any()))
                    .thenReturn(new PageImpl<>(List.of(execution), pageable, 1));

            AdminAiExecutionFilter filter = new AdminAiExecutionFilter(
                    null, null, providerId, "gpt-4o", "ROADMAP_GENERATION", "GENERATE", "SUCCEEDED", null);

            Page<AdminAiExecutionListResponse> result = service.listExecutions(filter, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            AdminAiExecutionListResponse item = result.getContent().get(0);
            assertThat(item.id()).isEqualTo(executionId);
            assertThat(item.providerId()).isEqualTo(providerId);
            assertThat(item.providerName()).isEqualTo("OpenAI");
            assertThat(item.model()).isEqualTo("gpt-4o");
            assertThat(item.purpose()).isEqualTo(AiPurpose.ROADMAP_GENERATION);
            assertThat(item.operation()).isEqualTo(AiExecutionOperation.GENERATE);
            assertThat(item.status()).isEqualTo(AiExecutionStatus.SUCCEEDED);
            assertThat(item.createdAt()).isEqualTo(createdAt);
        }
    }

    @Nested
    @DisplayName("Detail & Timeline Tests")
    class DetailAndTimelineTests {

        @Test
        void getExecutionDetail_whenNotFound_throwsNotFoundException() {
            UUID id = UUID.randomUUID();
            when(executionRepository.findAdminDetailById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getExecutionDetail(id))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).errorCode())
                            .isEqualTo(ErrorCode.AI_EXECUTION_NOT_FOUND));
        }

        @Test
        void getExecutionDetail_whenMultipleRetries_buildsSyntheticRetryEvents() {
            UUID executionId = UUID.randomUUID();
            UUID providerId = UUID.randomUUID();
            Instant createdAt = Instant.parse("2026-09-22T10:00:00Z");
            Instant startedAt = Instant.parse("2026-09-22T10:00:02Z");
            Instant completedAt = Instant.parse("2026-09-22T10:00:15Z");

            AiProvider provider = mock(AiProvider.class);
            when(provider.getId()).thenReturn(providerId);
            when(provider.getDisplayName()).thenReturn("Anthropic");

            AiProviderConfig config = mock(AiProviderConfig.class);
            when(config.getModel()).thenReturn("claude-3-5-sonnet");
            when(config.getProvider()).thenReturn(provider);

            AiExecution execution = mock(AiExecution.class);
            when(execution.getId()).thenReturn(executionId);
            when(execution.getProviderConfig()).thenReturn(config);
            when(execution.getPurpose()).thenReturn(AiPurpose.DAILY_PLAN_GENERATION);
            when(execution.getOperation()).thenReturn(AiExecutionOperation.REGENERATE);
            when(execution.getStatus()).thenReturn(AiExecutionStatus.FAILED);
            when(execution.getAttemptCount()).thenReturn(3); // 3 attempts -> 2 retries
            when(execution.getCreatedAt()).thenReturn(createdAt);
            when(execution.getStartedAt()).thenReturn(startedAt);
            when(execution.getCompletedAt()).thenReturn(completedAt);
            when(execution.getLatencyMs()).thenReturn(13000L);
            when(execution.getInputTokens()).thenReturn(1500);
            when(execution.getOutputTokens()).thenReturn(200);
            when(execution.getFailureCode()).thenReturn("RATE_LIMIT_EXCEEDED");
            when(execution.getFailureMessage()).thenReturn("Rate limit reached on provider");

            when(executionRepository.findAdminDetailById(executionId)).thenReturn(Optional.of(execution));

            AdminAiExecutionDetailResponse response = service.getExecutionDetail(executionId);

            assertThat(response.id()).isEqualTo(executionId);
            assertThat(response.providerId()).isEqualTo(providerId);
            assertThat(response.providerName()).isEqualTo("Anthropic");
            assertThat(response.model()).isEqualTo("claude-3-5-sonnet");
            assertThat(response.purpose()).isEqualTo(AiPurpose.DAILY_PLAN_GENERATION);
            assertThat(response.operation()).isEqualTo(AiExecutionOperation.REGENERATE);
            assertThat(response.status()).isEqualTo(AiExecutionStatus.FAILED);
            assertThat(response.attemptCount()).isEqualTo(3);
            assertThat(response.latencyMs()).isEqualTo(13000L);
            assertThat(response.inputTokens()).isEqualTo(1500);
            assertThat(response.outputTokens()).isEqualTo(200);
            assertThat(response.failureCode()).isEqualTo("RATE_LIMIT_EXCEEDED");
            assertThat(response.failureMessage()).isEqualTo("Rate limit reached on provider");

            // Verify Timeline Events
            List<AdminAiExecutionTimelineEvent> timeline = response.timeline();
            assertThat(timeline).hasSize(5);

            // 1. QUEUED
            assertThat(timeline.get(0).eventName()).isEqualTo("QUEUED");
            assertThat(timeline.get(0).timestamp()).isEqualTo(createdAt);
            assertThat(timeline.get(0).synthetic()).isFalse();

            // 2. RUNNING
            assertThat(timeline.get(1).eventName()).isEqualTo("RUNNING");
            assertThat(timeline.get(1).timestamp()).isEqualTo(startedAt);
            assertThat(timeline.get(1).synthetic()).isFalse();

            // 3. RETRY Attempt 2 (Synthetic)
            assertThat(timeline.get(2).eventName()).isEqualTo("RETRY (Attempt 2)");
            assertThat(timeline.get(2).timestamp()).isNull();
            assertThat(timeline.get(2).synthetic()).isTrue();

            // 4. RETRY Attempt 3 (Synthetic)
            assertThat(timeline.get(3).eventName()).isEqualTo("RETRY (Attempt 3)");
            assertThat(timeline.get(3).timestamp()).isNull();
            assertThat(timeline.get(3).synthetic()).isTrue();

            // 5. Final Status: FAILED
            assertThat(timeline.get(4).eventName()).isEqualTo("FAILED");
            assertThat(timeline.get(4).timestamp()).isEqualTo(completedAt);
            assertThat(timeline.get(4).synthetic()).isFalse();
        }
    }
}
