package com.codegym.aiplanning.service.ai.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.admin.ai.dto.AiPromptPreviewRequest;
import com.codegym.aiplanning.controller.admin.ai.dto.AiPromptPreviewResponse;
import com.codegym.aiplanning.controller.admin.ai.dto.AiPromptResponse;
import com.codegym.aiplanning.controller.admin.ai.dto.CreateAiPromptDraftRequest;
import com.codegym.aiplanning.controller.admin.ai.dto.UpdateAiPromptDraftRequest;
import com.codegym.aiplanning.entity.ai.AiPromptStatus;
import com.codegym.aiplanning.entity.ai.AiPromptTemplate;
import com.codegym.aiplanning.entity.ai.AiPurpose;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.repository.ai.AiPromptTemplateRepository;
import com.codegym.aiplanning.service.audit.AuditLogService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminAiPromptServiceImplTest {

    @Mock
    private AiPromptTemplateRepository repository;

    @Mock
    private SystemPromptTemplateValidator validator;

    @Mock
    private AuditLogService auditLogService;

    private AdminAiPromptServiceImpl service;
    private UUID actorId;
    private String actorEmail;

    @BeforeEach
    void setUp() {
        service = new AdminAiPromptServiceImpl(repository, validator, auditLogService);
        actorId = UUID.randomUUID();
        actorEmail = "admin@example.com";
    }

    @Test
    @DisplayName("createDraft assigns next sequential version number and logs audit")
    void createDraft_success() {
        when(repository.findMaxVersionNumberByPurpose(AiPurpose.ROADMAP_GENERATION)).thenReturn(2);
        when(repository.saveAndFlush(any())).thenAnswer(inv -> {
            AiPromptTemplate t = inv.getArgument(0);
            ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
            return t;
        });

        CreateAiPromptDraftRequest request = new CreateAiPromptDraftRequest(
                AiPurpose.ROADMAP_GENERATION,
                "New roadmap prompt in {{language}}");

        AiPromptResponse response = service.createDraft(actorId, actorEmail, request);

        assertThat(response.versionNumber()).isEqualTo(3);
        assertThat(response.status()).isEqualTo(AiPromptStatus.DRAFT);
        assertThat(response.isActive()).isFalse();

        verify(validator).validateContent(AiPurpose.ROADMAP_GENERATION, request.content());
        verify(auditLogService).logAction(
                eq(actorId),
                eq(actorEmail),
                eq(AuditEventAction.AI_PROMPT_DRAFT_CREATED),
                eq("AiPromptTemplate"),
                any());
    }

    @Test
    @DisplayName("updateDraft fails with PROMPT_IMMUTABLE if template is published")
    void updateDraft_published_throwsImmutable() {
        UUID promptId = UUID.randomUUID();
        AiPromptTemplate template = AiPromptTemplate.createDraft(AiPurpose.ROADMAP_GENERATION, 1, "Content", false);
        template.publish();
        ReflectionTestUtils.setField(template, "id", promptId);
        ReflectionTestUtils.setField(template, "version", 0L);

        when(repository.findByIdForUpdate(promptId)).thenReturn(Optional.of(template));

        UpdateAiPromptDraftRequest request = new UpdateAiPromptDraftRequest("New content", 0L);

        assertThatThrownBy(() -> service.updateDraft(actorId, actorEmail, promptId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo(ErrorCode.PROMPT_IMMUTABLE));
    }

    @Test
    @DisplayName("updateDraft checks optimistic lock version")
    void updateDraft_versionMismatch_throwsConcurrentModification() {
        UUID promptId = UUID.randomUUID();
        AiPromptTemplate template = AiPromptTemplate.createDraft(AiPurpose.ROADMAP_GENERATION, 1, "Content", false);
        ReflectionTestUtils.setField(template, "id", promptId);
        ReflectionTestUtils.setField(template, "version", 2L);

        when(repository.findByIdForUpdate(promptId)).thenReturn(Optional.of(template));

        UpdateAiPromptDraftRequest request = new UpdateAiPromptDraftRequest("New content", 1L);

        assertThatThrownBy(() -> service.updateDraft(actorId, actorEmail, promptId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION));
    }

    @Test
    @DisplayName("publish transitions DRAFT to PUBLISHED without activating")
    void publish_success() {
        UUID promptId = UUID.randomUUID();
        AiPromptTemplate template = AiPromptTemplate.createDraft(AiPurpose.ROADMAP_GENERATION, 1, "Content", false);
        ReflectionTestUtils.setField(template, "id", promptId);
        ReflectionTestUtils.setField(template, "version", 0L);

        when(repository.findByIdForUpdate(promptId)).thenReturn(Optional.of(template));
        when(repository.saveAndFlush(any())).thenReturn(template);

        AiPromptResponse response = service.publish(actorId, actorEmail, promptId, 0L);

        assertThat(response.status()).isEqualTo(AiPromptStatus.PUBLISHED);
        assertThat(response.isActive()).isFalse();

        verify(auditLogService).logAction(
                eq(actorId),
                eq(actorEmail),
                eq(AuditEventAction.AI_PROMPT_PUBLISHED),
                eq("AiPromptTemplate"),
                eq(promptId.toString()));
    }

    @Test
    @DisplayName("activate deactivates previous active and activates selected PUBLISHED template")
    void activate_success() {
        UUID promptId = UUID.randomUUID();
        AiPromptTemplate template = AiPromptTemplate.createDraft(AiPurpose.ROADMAP_GENERATION, 1, "Content", false);
        template.publish();
        ReflectionTestUtils.setField(template, "id", promptId);
        ReflectionTestUtils.setField(template, "version", 1L);

        when(repository.findByIdForUpdate(promptId)).thenReturn(Optional.of(template));
        when(repository.saveAndFlush(any())).thenReturn(template);

        AiPromptResponse response = service.activate(actorId, actorEmail, promptId, 1L);

        assertThat(response.isActive()).isTrue();
        verify(repository).deactivateCurrentActive(eq(AiPurpose.ROADMAP_GENERATION), any(Instant.class));
        verify(auditLogService).logAction(
                eq(actorId),
                eq(actorEmail),
                eq(AuditEventAction.AI_PROMPT_ACTIVATED),
                eq("AiPromptTemplate"),
                eq(promptId.toString()));
    }

    @Test
    @DisplayName("activate fails on ARCHIVED template")
    void activate_archived_throwsError() {
        UUID promptId = UUID.randomUUID();
        AiPromptTemplate template = AiPromptTemplate.createDraft(AiPurpose.ROADMAP_GENERATION, 1, "Content", false);
        template.archive();
        ReflectionTestUtils.setField(template, "id", promptId);
        ReflectionTestUtils.setField(template, "version", 1L);

        when(repository.findByIdForUpdate(promptId)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.activate(actorId, actorEmail, promptId, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo(ErrorCode.PROMPT_ARCHIVED_CANNOT_ACTIVATE));
    }

    @Test
    @DisplayName("rollback switches active pointer to an older PUBLISHED version")
    void rollback_success() {
        UUID promptId = UUID.randomUUID();
        AiPromptTemplate template = AiPromptTemplate.createDraft(AiPurpose.ROADMAP_GENERATION, 1, "V1 Content", false);
        template.publish();
        ReflectionTestUtils.setField(template, "id", promptId);
        ReflectionTestUtils.setField(template, "version", 2L);

        when(repository.findByIdForUpdate(promptId)).thenReturn(Optional.of(template));
        when(repository.saveAndFlush(any())).thenReturn(template);

        AiPromptResponse response = service.rollback(actorId, actorEmail, promptId, 2L);

        assertThat(response.isActive()).isTrue();
        verify(repository).deactivateCurrentActive(eq(AiPurpose.ROADMAP_GENERATION), any(Instant.class));
        verify(auditLogService).logAction(
                eq(actorId),
                eq(actorEmail),
                eq(AuditEventAction.AI_PROMPT_ROLLBACK),
                eq("AiPromptTemplate"),
                eq(promptId.toString()));
    }

    @Test
    @DisplayName("rollback fails if target version is ARCHIVED")
    void rollback_archived_throwsError() {
        UUID promptId = UUID.randomUUID();
        AiPromptTemplate template = AiPromptTemplate.createDraft(AiPurpose.ROADMAP_GENERATION, 1, "V1 Content", false);
        template.archive();
        ReflectionTestUtils.setField(template, "id", promptId);
        ReflectionTestUtils.setField(template, "version", 2L);

        when(repository.findByIdForUpdate(promptId)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.rollback(actorId, actorEmail, promptId, 2L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo(ErrorCode.PROMPT_ARCHIVED_CANNOT_ACTIVATE));
    }

    @Test
    @DisplayName("archive sets status to ARCHIVED and isActive to false")
    void archive_success() {
        UUID promptId = UUID.randomUUID();
        AiPromptTemplate template = AiPromptTemplate.createDraft(AiPurpose.ROADMAP_GENERATION, 1, "V1 Content", false);
        template.publish();
        template.activate();
        ReflectionTestUtils.setField(template, "id", promptId);
        ReflectionTestUtils.setField(template, "version", 3L);

        when(repository.findByIdForUpdate(promptId)).thenReturn(Optional.of(template));
        when(repository.saveAndFlush(any())).thenReturn(template);

        service.archive(actorId, actorEmail, promptId, 3L);

        assertThat(template.isArchived()).isTrue();
        assertThat(template.isActive()).isFalse();

        verify(auditLogService).logAction(
                eq(actorId),
                eq(actorEmail),
                eq(AuditEventAction.AI_PROMPT_ARCHIVED),
                eq("AiPromptTemplate"),
                eq(promptId.toString()));
    }

    @Test
    @DisplayName("preview delegates to validator without external provider call")
    void preview_delegatesToValidator() {
        AiPromptPreviewRequest request = new AiPromptPreviewRequest(
                AiPurpose.DAILY_PLAN_GENERATION,
                "Plan for {{availableMinutes}} min in {{language}}",
                null);

        when(validator.renderPreview(eq(AiPurpose.DAILY_PLAN_GENERATION), any(), any()))
                .thenReturn("Plan for 120 min in vi-VN");

        AiPromptPreviewResponse response = service.preview(request);

        assertThat(response.renderedContent()).isEqualTo("Plan for 120 min in vi-VN");
        verify(validator).renderPreview(eq(AiPurpose.DAILY_PLAN_GENERATION), eq(request.content()), any());
    }

    @Test
    @DisplayName("Edge Case: activate fails if template is still in DRAFT status")
    void activate_draft_throwsPromptNotPublished() {
        UUID promptId = UUID.randomUUID();
        AiPromptTemplate template = AiPromptTemplate.createDraft(AiPurpose.ROADMAP_GENERATION, 1, "Content", false);
        ReflectionTestUtils.setField(template, "id", promptId);
        ReflectionTestUtils.setField(template, "version", 0L);

        when(repository.findByIdForUpdate(promptId)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.activate(actorId, actorEmail, promptId, 0L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo(ErrorCode.PROMPT_NOT_PUBLISHED));
    }

    @Test
    @DisplayName("Edge Case: publish fails if template is already PUBLISHED")
    void publish_alreadyPublished_throwsInvalidStatusTransition() {
        UUID promptId = UUID.randomUUID();
        AiPromptTemplate template = AiPromptTemplate.createDraft(AiPurpose.ROADMAP_GENERATION, 1, "Content", false);
        template.publish();
        ReflectionTestUtils.setField(template, "id", promptId);
        ReflectionTestUtils.setField(template, "version", 1L);

        when(repository.findByIdForUpdate(promptId)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.publish(actorId, actorEmail, promptId, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION));
    }

    @Test
    @DisplayName("Edge Case: rollback fails if target template is still in DRAFT status")
    void rollback_draft_throwsPromptNotPublished() {
        UUID promptId = UUID.randomUUID();
        AiPromptTemplate template = AiPromptTemplate.createDraft(AiPurpose.ROADMAP_GENERATION, 1, "Content", false);
        ReflectionTestUtils.setField(template, "id", promptId);
        ReflectionTestUtils.setField(template, "version", 0L);

        when(repository.findByIdForUpdate(promptId)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.rollback(actorId, actorEmail, promptId, 0L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo(ErrorCode.PROMPT_NOT_PUBLISHED));
    }

    @Test
    @DisplayName("Edge Case: updateDraft fails if template is ARCHIVED")
    void updateDraft_archived_throwsPromptImmutable() {
        UUID promptId = UUID.randomUUID();
        AiPromptTemplate template = AiPromptTemplate.createDraft(AiPurpose.ROADMAP_GENERATION, 1, "Content", false);
        template.archive();
        ReflectionTestUtils.setField(template, "id", promptId);
        ReflectionTestUtils.setField(template, "version", 1L);

        when(repository.findByIdForUpdate(promptId)).thenReturn(Optional.of(template));

        UpdateAiPromptDraftRequest request = new UpdateAiPromptDraftRequest("Updated content", 1L);

        assertThatThrownBy(() -> service.updateDraft(actorId, actorEmail, promptId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo(ErrorCode.PROMPT_IMMUTABLE));
    }
}
