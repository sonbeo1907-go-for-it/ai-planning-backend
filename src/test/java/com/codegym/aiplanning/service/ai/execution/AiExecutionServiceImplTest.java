package com.codegym.aiplanning.service.ai.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.ai.dto.AiExecutionResponse;
import com.codegym.aiplanning.entity.ai.AiExecution;
import com.codegym.aiplanning.entity.ai.AiExecutionInput;
import com.codegym.aiplanning.entity.ai.AiExecutionOperation;
import com.codegym.aiplanning.entity.ai.AiExecutionStatus;
import com.codegym.aiplanning.entity.ai.AiExecutionTargetType;
import com.codegym.aiplanning.entity.ai.AiProviderConfig;
import com.codegym.aiplanning.entity.ai.AiPurpose;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.repository.ai.AiExecutionInputRepository;
import com.codegym.aiplanning.repository.ai.AiExecutionRepository;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.service.ai.AiProviderSelector;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.service.roadmap.impl.AiRoadmapPersistenceService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AiExecutionServiceImplTest {

    @Mock
    private AiExecutionRepository executionRepository;

    @Mock
    private AiExecutionInputRepository inputRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private AiProviderSelector providerSelector;

    @Mock
    private AiRoadmapPersistenceService roadmapPersistenceService;

    @Mock
    private AuditLogService auditLogService;

    private AiExecutionServiceImpl service;
    private UUID ownerId;
    private UUID roadmapId;
    private UserAccount owner;
    private AiProviderConfig providerConfig;

    @BeforeEach
    void setUp() {
        service = new AiExecutionServiceImpl(
                executionRepository,
                inputRepository,
                userAccountRepository,
                providerSelector,
                roadmapPersistenceService,
                auditLogService);
        ownerId = UUID.randomUUID();
        roadmapId = UUID.randomUUID();
        owner = org.mockito.Mockito.mock(UserAccount.class);
        providerConfig = org.mockito.Mockito.mock(AiProviderConfig.class);
    }

    @Test
    void generationReturnsQueuedExecutionWithoutCallingTheProvider() {
        UUID executionId = UUID.randomUUID();
        UUID providerConfigId = UUID.randomUUID();
        when(owner.getEmail()).thenReturn("user@example.com");
        when(providerConfig.getId()).thenReturn(providerConfigId);
        when(executionRepository
                        .findFirstByOwnerIdAndTargetTypeAndTargetIdAndPurposeAndStatusInOrderByCreatedAtDesc(
                                any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(userAccountRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(providerSelector.requireDefault(AiPurpose.ROADMAP_GENERATION))
                .thenReturn(providerConfig);
        when(executionRepository.saveAndFlush(any(AiExecution.class)))
                .thenAnswer(invocation -> persistedExecution(
                        invocation.getArgument(0), executionId));

        AiExecutionResponse response = service.submitRoadmapGeneration(
                ownerId,
                roadmapId,
                List.of(UUID.randomUUID()),
                "roadmap-generation-1");

        assertEquals(executionId, response.id());
        assertEquals(AiExecutionStatus.QUEUED, response.status());
        assertEquals(AiExecutionOperation.GENERATE, response.operation());
        assertEquals(roadmapId, response.targetId());
        verify(roadmapPersistenceService).prepare(any(), any(), any());
        verify(inputRepository, never()).save(any());
        verify(auditLogService).logAction(
                ownerId,
                "user@example.com",
                AuditEventAction.AI_EXECUTION_QUEUED,
                "AiExecution",
                executionId.toString());
    }

    @Test
    void regenerationStoresPrivateInputOutsideTheExecutionRecord() {
        UUID executionId = UUID.randomUUID();
        when(owner.getEmail()).thenReturn("user@example.com");
        when(executionRepository
                        .findFirstByOwnerIdAndTargetTypeAndTargetIdAndPurposeAndStatusInOrderByCreatedAtDesc(
                                any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(userAccountRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(providerSelector.requireDefault(AiPurpose.ROADMAP_GENERATION))
                .thenReturn(providerConfig);
        when(executionRepository.saveAndFlush(any(AiExecution.class)))
                .thenAnswer(invocation -> persistedExecution(
                        invocation.getArgument(0), executionId));

        service.submitRoadmapRegeneration(
                ownerId,
                roadmapId,
                "  Increase practical work  ",
                null);

        ArgumentCaptor<AiExecutionInput> inputCaptor =
                ArgumentCaptor.forClass(AiExecutionInput.class);
        verify(inputRepository).save(inputCaptor.capture());
        assertEquals("Increase practical work", inputCaptor.getValue().getAdjustmentPrompt());
    }

    @Test
    void repeatedIdempotencyKeyReturnsTheOriginalExecution() {
        UUID executionId = UUID.randomUUID();
        AiExecution existing = persistedExecution(
                AiExecution.queue(
                        owner,
                        providerConfig,
                        AiPurpose.ROADMAP_GENERATION,
                        AiExecutionOperation.GENERATE,
                        AiExecutionTargetType.ROADMAP,
                        roadmapId,
                        "same-request"),
                executionId);
        when(executionRepository.findByOwnerIdAndIdempotencyKey(
                        ownerId, "same-request"))
                .thenReturn(Optional.of(existing));

        AiExecutionResponse response = service.submitRoadmapGeneration(
                ownerId, roadmapId, List.of(), " same-request ");

        assertEquals(executionId, response.id());
        verify(roadmapPersistenceService, never()).prepare(any(), any(), any());
        verify(providerSelector, never()).requireDefault(any());
        verify(executionRepository, never()).saveAndFlush(any());
    }

    @Test
    void executionLookupIsOwnerScoped() {
        UUID executionId = UUID.randomUUID();
        when(executionRepository.findByIdAndOwnerId(executionId, ownerId))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.getOwnedExecution(ownerId, executionId));

        assertEquals(ErrorCode.AI_EXECUTION_NOT_FOUND, exception.errorCode());
    }

    private AiExecution persistedExecution(AiExecution execution, UUID executionId) {
        Instant now = Instant.now();
        ReflectionTestUtils.setField(execution, "id", executionId);
        ReflectionTestUtils.setField(execution, "createdAt", now);
        ReflectionTestUtils.setField(execution, "updatedAt", now);
        return execution;
    }
}
