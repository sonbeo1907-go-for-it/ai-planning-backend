package com.codegym.aiplanning.service.daily.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.daily.dto.DailyPlanItemAiSuggestionResponse;
import com.codegym.aiplanning.controller.daily.dto.DailyPlanItemDetailResponse;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.daily.DailyPlan;
import com.codegym.aiplanning.entity.daily.DailyPlanItem;
import com.codegym.aiplanning.entity.daily.DailyPlanItemAiSuggestion;
import com.codegym.aiplanning.entity.daily.DailyPlanItemAiSuggestionReference;
import com.codegym.aiplanning.entity.daily.DailyPlanItemAiSuggestionStep;
import com.codegym.aiplanning.entity.daily.TaskAiReferenceType;
import com.codegym.aiplanning.repository.daily.DailyPlanItemAiSuggestionReferenceRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanItemAiSuggestionRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanItemAiSuggestionStepRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanItemRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanVersionRepository;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.service.daily.TaskAiSuggestionService;
import com.codegym.aiplanning.service.daily.ai.TaskSuggestionAiGenerator;
import com.codegym.aiplanning.service.daily.ai.TaskSuggestionContext;
import com.codegym.aiplanning.service.daily.ai.TaskSuggestionContextBuilder;
import com.codegym.aiplanning.service.daily.ai.ValidatedTaskSuggestion;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskAiSuggestionServiceImpl implements TaskAiSuggestionService {

    private final DailyPlanRepository dailyPlanRepository;
    private final DailyPlanVersionRepository dailyPlanVersionRepository;
    private final DailyPlanItemRepository dailyPlanItemRepository;
    private final DailyPlanItemAiSuggestionRepository suggestionRepository;
    private final DailyPlanItemAiSuggestionStepRepository stepRepository;
    private final DailyPlanItemAiSuggestionReferenceRepository referenceRepository;
    private final TaskSuggestionContextBuilder contextBuilder;
    private final TaskSuggestionAiGenerator aiGenerator;
    private final AuditLogService auditLogService;

    public TaskAiSuggestionServiceImpl(
            DailyPlanRepository dailyPlanRepository,
            DailyPlanVersionRepository dailyPlanVersionRepository,
            DailyPlanItemRepository dailyPlanItemRepository,
            DailyPlanItemAiSuggestionRepository suggestionRepository,
            DailyPlanItemAiSuggestionStepRepository stepRepository,
            DailyPlanItemAiSuggestionReferenceRepository referenceRepository,
            TaskSuggestionContextBuilder contextBuilder,
            TaskSuggestionAiGenerator aiGenerator,
            AuditLogService auditLogService) {
        this.dailyPlanRepository = dailyPlanRepository;
        this.dailyPlanVersionRepository = dailyPlanVersionRepository;
        this.dailyPlanItemRepository = dailyPlanItemRepository;
        this.suggestionRepository = suggestionRepository;
        this.stepRepository = stepRepository;
        this.referenceRepository = referenceRepository;
        this.contextBuilder = contextBuilder;
        this.aiGenerator = aiGenerator;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional(readOnly = true)
    public DailyPlanItemDetailResponse getTaskDetail(UUID planId, UUID itemId, Jwt actorJwt) {
        UUID userId = extractUserId(actorJwt);
        requirePlanForUser(planId, userId);
        DailyPlanItem item = requireItemInPlan(planId, itemId);
        return DailyPlanItemDetailResponse.of(item, suggestionResponse(itemId));
    }

    @Override
    @Transactional
    public DailyPlanItemAiSuggestionResponse generateSuggestion(
            UUID planId, UUID itemId, String idempotencyKey, Jwt actorJwt) {
        UUID userId = extractUserId(actorJwt);
        String username = extractUsername(actorJwt);
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        DailyPlan plan = requirePlanForUser(planId, userId);
        DailyPlanItem item = requireItemInPlan(planId, itemId);

        DailyPlanItemAiSuggestion existing =
                suggestionRepository.findByDailyPlanItemId(itemId).orElse(null);
        if (existing != null) {
            return suggestionResponse(itemId);
        }

        ValidatedTaskSuggestion generated =
                aiGenerator.generate(contextBuilder.build(plan, item));
        DailyPlanItemAiSuggestion suggestion = suggestionRepository.saveAndFlush(
                DailyPlanItemAiSuggestion.create(
                        itemId, generated.shortDescription(), normalizedKey));
        saveChildren(suggestion.getId(), generated);
        auditLogService.logAction(
                userId,
                username,
                AuditEventAction.TASK_AI_SUGGESTION_GENERATED,
                "DailyPlanItem",
                itemId.toString());
        return suggestionResponse(itemId);
    }

    @Override
    @Transactional
    public DailyPlanItemAiSuggestionResponse regenerateSuggestion(
            UUID planId, UUID itemId, Jwt actorJwt) {
        UUID userId = extractUserId(actorJwt);
        String username = extractUsername(actorJwt);
        DailyPlan plan = requirePlanForUser(planId, userId);
        DailyPlanItem item = requireItemInPlan(planId, itemId);

        DailyPlanItemAiSuggestion suggestion =
                suggestionRepository.findByDailyPlanItemId(itemId).orElse(null);

        ValidatedTaskSuggestion generated =
                aiGenerator.generate(contextBuilder.build(plan, item));

        if (suggestion == null) {
            suggestion = suggestionRepository.saveAndFlush(DailyPlanItemAiSuggestion.create(
                    itemId, generated.shortDescription(), null));
        } else {
            suggestion.updateContent(generated.shortDescription(), null);
            suggestionRepository.saveAndFlush(suggestion);
            stepRepository.deleteBySuggestionId(suggestion.getId());
            referenceRepository.deleteBySuggestionId(suggestion.getId());
            stepRepository.flush();
            referenceRepository.flush();
        }
        saveChildren(suggestion.getId(), generated);

        auditLogService.logAction(
                userId,
                username,
                AuditEventAction.TASK_AI_SUGGESTION_REGENERATED,
                "DailyPlanItem",
                itemId.toString());
        return suggestionResponse(itemId);
    }

    private void saveChildren(UUID suggestionId, ValidatedTaskSuggestion generated) {
        List<DailyPlanItemAiSuggestionStep> steps = new ArrayList<>();
        for (int index = 0; index < generated.steps().size(); index++) {
            steps.add(DailyPlanItemAiSuggestionStep.create(
                    suggestionId, index, generated.steps().get(index)));
        }
        stepRepository.saveAll(steps);

        List<DailyPlanItemAiSuggestionReference> references = new ArrayList<>();
        for (ValidatedTaskSuggestion.ValidatedReference reference : generated.references()) {
            references.add(DailyPlanItemAiSuggestionReference.create(
                    suggestionId,
                    reference.type(),
                    reference.title(),
                    reference.type() == TaskAiReferenceType.LINK ? reference.url() : null,
                    reference.type() == TaskAiReferenceType.DOCUMENT ? reference.documentId() : null,
                    reference.verified()));
        }
        referenceRepository.saveAll(references);
    }

    private DailyPlanItemAiSuggestionResponse suggestionResponse(UUID itemId) {
        DailyPlanItemAiSuggestion suggestion =
                suggestionRepository.findByDailyPlanItemId(itemId).orElse(null);
        if (suggestion == null) {
            return null;
        }
        return DailyPlanItemAiSuggestionResponse.from(
                suggestion,
                stepRepository.findBySuggestionIdOrderByOrderIndexAsc(suggestion.getId()),
                referenceRepository.findBySuggestionId(suggestion.getId()));
    }

    private DailyPlan requirePlanForUser(UUID planId, UUID userId) {
        return dailyPlanRepository
                .findByIdAndUserId(planId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Daily Plan resource was not found."));
    }

    private DailyPlanItem requireItemInPlan(UUID planId, UUID itemId) {
        DailyPlanItem item = dailyPlanItemRepository
                .findById(itemId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.DAILY_PLAN_ITEM_NOT_FOUND,
                        "Task item not found: " + itemId));
        dailyPlanVersionRepository
                .findByIdAndDailyPlanId(item.getDailyPlanVersionId(), planId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.DAILY_PLAN_ITEM_NOT_FOUND,
                        "Task item does not belong to this daily plan."));
        return item;
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() > 100) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "Idempotency-Key must not exceed 100 characters.");
        }
        return normalized;
    }

    private UUID extractUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new BusinessException(
                    ErrorCode.AUTHENTICATION_REQUIRED, "Authentication is invalid.");
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.AUTHENTICATION_REQUIRED, "Authentication is invalid.");
        }
    }

    private String extractUsername(Jwt jwt) {
        if (jwt == null) {
            return "system";
        }
        String username = jwt.getClaimAsString("preferred_username");
        if (username == null || username.isBlank()) {
            username = jwt.getClaimAsString("email");
        }
        if (username == null || username.isBlank()) {
            username = jwt.getSubject();
        }
        return username != null ? username : "unknown";
    }
}

