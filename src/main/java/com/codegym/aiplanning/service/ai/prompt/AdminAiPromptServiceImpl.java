package com.codegym.aiplanning.service.ai.prompt;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAiPromptServiceImpl implements AdminAiPromptService {

    private static final Logger log = LoggerFactory.getLogger(AdminAiPromptServiceImpl.class);

    private final AiPromptTemplateRepository repository;
    private final SystemPromptTemplateValidator validator;
    private final AuditLogService auditLogService;

    public AdminAiPromptServiceImpl(
            AiPromptTemplateRepository repository,
            SystemPromptTemplateValidator validator,
            AuditLogService auditLogService) {
        this.repository = repository;
        this.validator = validator;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    public AiPromptResponse createDraft(UUID actorId, String actorEmail, CreateAiPromptDraftRequest request) {
        validator.validateContent(request.purpose(), request.content());

        int nextVersion = repository.findMaxVersionNumberByPurpose(request.purpose()) + 1;
        AiPromptTemplate draft = AiPromptTemplate.createDraft(request.purpose(), nextVersion, request.content(), false);

        try {
            draft = repository.saveAndFlush(draft);
        } catch (DataIntegrityViolationException exception) {
            log.warn("Concurrent creation detected for purpose {}. Retrying version computation.", request.purpose());
            nextVersion = repository.findMaxVersionNumberByPurpose(request.purpose()) + 1;
            draft = AiPromptTemplate.createDraft(request.purpose(), nextVersion, request.content(), false);
            try {
                draft = repository.saveAndFlush(draft);
            } catch (DataIntegrityViolationException retryEx) {
                throw new BusinessException(
                        ErrorCode.CONCURRENT_MODIFICATION,
                        "Concurrent prompt template creation. Please retry.");
            }
        }

        auditLogService.logAction(
                actorId,
                actorEmail,
                AuditEventAction.AI_PROMPT_DRAFT_CREATED,
                "AiPromptTemplate",
                draft.getId().toString());

        return AiPromptResponse.from(draft);
    }

    @Override
    @Transactional
    public AiPromptResponse updateDraft(UUID actorId, String actorEmail, UUID promptId, UpdateAiPromptDraftRequest request) {
        AiPromptTemplate template = findTemplateForUpdate(promptId);
        checkOptimisticLock(template, request.version());

        if (template.getStatus() != AiPromptStatus.DRAFT) {
            throw new BusinessException(
                    ErrorCode.PROMPT_IMMUTABLE,
                    "Published and archived prompt templates are immutable. Create a new draft instead.");
        }

        validator.validateContent(template.getPurpose(), request.content());
        template.updateDraftContent(request.content());
        template = repository.saveAndFlush(template);

        auditLogService.logAction(
                actorId,
                actorEmail,
                AuditEventAction.AI_PROMPT_DRAFT_UPDATED,
                "AiPromptTemplate",
                template.getId().toString());

        return AiPromptResponse.from(template);
    }

    @Override
    @Transactional
    public AiPromptResponse publish(UUID actorId, String actorEmail, UUID promptId, long expectedVersion) {
        AiPromptTemplate template = findTemplateForUpdate(promptId);
        checkOptimisticLock(template, expectedVersion);

        if (template.getStatus() != AiPromptStatus.DRAFT) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATUS_TRANSITION,
                    "Only DRAFT prompt templates can be published.");
        }

        template.publish();
        template = repository.saveAndFlush(template);

        auditLogService.logAction(
                actorId,
                actorEmail,
                AuditEventAction.AI_PROMPT_PUBLISHED,
                "AiPromptTemplate",
                template.getId().toString());

        return AiPromptResponse.from(template);
    }

    @Override
    @Transactional
    public AiPromptResponse activate(UUID actorId, String actorEmail, UUID promptId, long expectedVersion) {
        AiPromptTemplate template = findTemplateForUpdate(promptId);
        checkOptimisticLock(template, expectedVersion);

        if (template.isArchived()) {
            throw new BusinessException(
                    ErrorCode.PROMPT_ARCHIVED_CANNOT_ACTIVATE,
                    "Cannot activate an ARCHIVED prompt template.");
        }
        if (!template.isPublished()) {
            throw new BusinessException(
                    ErrorCode.PROMPT_NOT_PUBLISHED,
                    "Only PUBLISHED prompt templates can be activated.");
        }

        repository.deactivateCurrentActive(template.getPurpose(), Instant.now());
        template.activate();
        template = repository.saveAndFlush(template);

        auditLogService.logAction(
                actorId,
                actorEmail,
                AuditEventAction.AI_PROMPT_ACTIVATED,
                "AiPromptTemplate",
                template.getId().toString());

        return AiPromptResponse.from(template);
    }

    @Override
    @Transactional
    public AiPromptResponse rollback(UUID actorId, String actorEmail, UUID promptId, long expectedVersion) {
        AiPromptTemplate template = findTemplateForUpdate(promptId);
        checkOptimisticLock(template, expectedVersion);

        if (template.isArchived()) {
            throw new BusinessException(
                    ErrorCode.PROMPT_ARCHIVED_CANNOT_ACTIVATE,
                    "Cannot rollback to an ARCHIVED prompt template.");
        }
        if (!template.isPublished()) {
            throw new BusinessException(
                    ErrorCode.PROMPT_NOT_PUBLISHED,
                    "Cannot rollback to an un-published prompt template.");
        }

        repository.deactivateCurrentActive(template.getPurpose(), Instant.now());
        template.activate();
        template = repository.saveAndFlush(template);

        auditLogService.logAction(
                actorId,
                actorEmail,
                AuditEventAction.AI_PROMPT_ROLLBACK,
                "AiPromptTemplate",
                template.getId().toString());

        return AiPromptResponse.from(template);
    }

    @Override
    @Transactional
    public void archive(UUID actorId, String actorEmail, UUID promptId, long expectedVersion) {
        AiPromptTemplate template = findTemplateForUpdate(promptId);
        checkOptimisticLock(template, expectedVersion);

        template.archive();
        repository.saveAndFlush(template);

        auditLogService.logAction(
                actorId,
                actorEmail,
                AuditEventAction.AI_PROMPT_ARCHIVED,
                "AiPromptTemplate",
                template.getId().toString());
    }

    @Override
    public AiPromptPreviewResponse preview(AiPromptPreviewRequest request) {
        String rendered = validator.renderPreview(request.purpose(), request.content(), request.syntheticData());
        Map<String, String> sampleData = validator.getDefaultSyntheticData(request.purpose());
        return new AiPromptPreviewResponse(rendered, sampleData);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiPromptResponse> listByPurpose(AiPurpose purpose) {
        return repository.findByPurposeOrderByVersionNumberDesc(purpose).stream()
                .map(AiPromptResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AiPromptResponse getById(UUID promptId) {
        return repository.findById(promptId)
                .map(AiPromptResponse::from)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.PROMPT_TEMPLATE_NOT_FOUND,
                        "Prompt template not found: " + promptId));
    }

    @Override
    @Transactional(readOnly = true)
    public com.codegym.aiplanning.controller.admin.ai.dto.AiPromptDefaultResponse getDefaultPrompt(AiPurpose purpose) {
        return repository.findFirstByPurposeAndIsSystemTrueOrderByVersionNumberDesc(purpose)
                .map(template -> new com.codegym.aiplanning.controller.admin.ai.dto.AiPromptDefaultResponse(template.getPurpose(), template.getContent()))
                .orElseGet(() -> {
                    String fallbackContent = DefaultSystemPrompts.getDefaultFor(purpose);
                    if (fallbackContent != null && !fallbackContent.isBlank()) {
                        return new com.codegym.aiplanning.controller.admin.ai.dto.AiPromptDefaultResponse(purpose, fallbackContent);
                    }
                    throw new BusinessException(
                            ErrorCode.PROMPT_TEMPLATE_NOT_FOUND,
                            "System default prompt not found for purpose: " + purpose);
                });
    }

    private AiPromptTemplate findTemplateForUpdate(UUID promptId) {
        return repository.findByIdForUpdate(promptId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.PROMPT_TEMPLATE_NOT_FOUND,
                        "Prompt template not found: " + promptId));
    }

    private void checkOptimisticLock(AiPromptTemplate template, long expectedVersion) {
        if (template.getVersion() != expectedVersion) {
            throw new BusinessException(
                    ErrorCode.CONCURRENT_MODIFICATION,
                    "Prompt template was modified concurrently by another user.");
        }
    }
}
