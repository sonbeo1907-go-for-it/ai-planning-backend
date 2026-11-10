package com.codegym.aiplanning.service.daily.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.daily.DailyPlan;
import com.codegym.aiplanning.entity.daily.DailyPlanItem;
import com.codegym.aiplanning.entity.daily.DailyPlanTaskStep;
import com.codegym.aiplanning.entity.daily.DailyPlanVersion;
import com.codegym.aiplanning.entity.daily.DailyPlanVersionOrigin;
import com.codegym.aiplanning.entity.daily.DailyPlanVersionStatus;
import com.codegym.aiplanning.repository.daily.DailyPlanItemRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanTaskStepRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanVersionRepository;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.service.daily.DailyPlanPersistenceService;
import com.codegym.aiplanning.service.daily.ai.DailyPlanAiResponse;
import com.codegym.aiplanning.service.daily.step.TaskStepValidator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyPlanPersistenceServiceImpl implements DailyPlanPersistenceService {

    private final DailyPlanRepository dailyPlanRepository;
    private final DailyPlanVersionRepository dailyPlanVersionRepository;
    private final DailyPlanItemRepository dailyPlanItemRepository;
    private final DailyPlanTaskStepRepository taskStepRepository;
    private final TaskStepValidator taskStepValidator;
    private final AuditLogService auditLogService;

    public DailyPlanPersistenceServiceImpl(
            DailyPlanRepository dailyPlanRepository,
            DailyPlanVersionRepository dailyPlanVersionRepository,
            DailyPlanItemRepository dailyPlanItemRepository,
            DailyPlanTaskStepRepository taskStepRepository,
            TaskStepValidator taskStepValidator,
            AuditLogService auditLogService) {
        this.dailyPlanRepository = dailyPlanRepository;
        this.dailyPlanVersionRepository = dailyPlanVersionRepository;
        this.dailyPlanItemRepository = dailyPlanItemRepository;
        this.taskStepRepository = taskStepRepository;
        this.taskStepValidator = taskStepValidator;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    public DailyPlanVersion persistAiGeneratedDraft(
            UUID planId,
            UUID userId,
            String username,
            int totalPlannedMinutes,
            String aiExplanation,
            boolean requiresUserDecision,
            String generationRequestKey,
            List<DailyPlanAiResponse.AiPlanItemDto> aiItems) {

        DailyPlan plan = dailyPlanRepository.findByIdAndUserIdForUpdate(planId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DAILY_PLAN_NOT_FOUND, "Daily plan not found"));

        // Retrieve latest version to get available minutes
        DailyPlanVersion latestVersion = dailyPlanVersionRepository.findTopByDailyPlanIdOrderByVersionNumberDesc(planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "No version found for this plan"));

        if (totalPlannedMinutes > latestVersion.getAvailableMinutes()) {
            throw new BusinessException(
                    ErrorCode.AI_OUTPUT_INVALID,
                    "AI planned minutes exceed the Daily Plan available-time budget.");
        }

        // Check if there is an existing DRAFT
        Optional<DailyPlanVersion> oldDraftOpt = dailyPlanVersionRepository.findByDailyPlanIdAndStatus(planId, DailyPlanVersionStatus.DRAFT);

        DailyPlanVersionOrigin origin = DailyPlanVersionOrigin.AI_GENERATED;
        if (oldDraftOpt.isPresent()) {
            DailyPlanVersion oldDraft = oldDraftOpt.get();
            // Important: Change old draft status to SUPERSEDED, DO NOT DELETE!
            oldDraft.supersede(Instant.now());
            dailyPlanVersionRepository.saveAndFlush(oldDraft);
            origin = DailyPlanVersionOrigin.AI_REGENERATED; // AI regenerating over an existing draft
        }

        // Determine next version number
        int nextNumber = latestVersion.getVersionNumber() + 1;

        // Create new DRAFT version
        DailyPlanVersion draft = DailyPlanVersion.create(
                planId,
                nextNumber,
                origin,
                latestVersion.getAvailableMinutes(),
                totalPlannedMinutes
        );
        draft.updateAiMetadata(aiExplanation, requiresUserDecision);
        draft.assignGenerationRequestKey(generationRequestKey);
        DailyPlanVersion savedDraft = dailyPlanVersionRepository.saveAndFlush(draft);

        // Build and save items
        List<DailyPlanItem> itemsToSave = new ArrayList<>();
        int orderIndex = 0;
        for (DailyPlanAiResponse.AiPlanItemDto aiItem : aiItems) {
            DailyPlanItem item = DailyPlanItem.create(
                    savedDraft.getId(),
                    aiItem.category(),
                    aiItem.title(),
                    aiItem.description(),
                    aiItem.plannedMinutes(),
                    orderIndex++,
                    aiItem.roadmapItemId()
            );
            if (aiItem.aiAdjustmentAction() != null) {
                item.setAiAdjustment(aiItem.aiAdjustmentAction(), aiItem.aiAdjustmentReason());
            }
            itemsToSave.add(item);
        }
        List<DailyPlanItem> savedItems = dailyPlanItemRepository.saveAll(itemsToSave);
        dailyPlanItemRepository.flush();
        persistGeneratedTaskSteps(aiItems, savedItems);

        auditLogService.logAction(
                userId,
                username,
                AuditEventAction.DAILY_PLAN_VERSION_CREATED,
                "DailyPlanVersion",
                savedDraft.getId().toString());

        return savedDraft;
    }

    private void persistGeneratedTaskSteps(
            List<DailyPlanAiResponse.AiPlanItemDto> aiItems,
            List<DailyPlanItem> savedItems) {
        List<DailyPlanTaskStep> allSteps = new ArrayList<>();
        for (int itemIndex = 0; itemIndex < aiItems.size(); itemIndex++) {
            DailyPlanAiResponse.AiPlanItemDto aiItem = aiItems.get(itemIndex);
            DailyPlanItem savedItem = savedItems.get(itemIndex);
            if (aiItem.steps() == null || aiItem.steps().isEmpty()) {
                throw new BusinessException(
                        ErrorCode.AI_OUTPUT_INVALID,
                        "An AI-generated Daily Plan task must contain Task Steps.");
            }
            List<DailyPlanAiResponse.AiTaskStepDto> orderedSteps = new ArrayList<>(
                    aiItem.steps());
            orderedSteps.sort(Comparator.comparing(
                    DailyPlanAiResponse.AiTaskStepDto::orderIndex));

            List<DailyPlanTaskStep> itemSteps = new ArrayList<>();
            for (int stepIndex = 0; stepIndex < orderedSteps.size(); stepIndex++) {
                DailyPlanAiResponse.AiTaskStepDto aiStep = orderedSteps.get(stepIndex);
                itemSteps.add(DailyPlanTaskStep.create(
                        savedItem.getId(),
                        taskStepValidator.normalizeRequiredTitle(aiStep.title()),
                        taskStepValidator.normalizeOptionalGuidance(aiStep.guidance()),
                        stepIndex,
                        aiStep.estimatedMinutes(),
                        aiStep.required()));
            }
            taskStepValidator.validateAll(
                    itemSteps,
                    savedItem.getPlannedMinutes(),
                    savedItem.getTitle());
            allSteps.addAll(itemSteps);
        }
        if (!allSteps.isEmpty()) {
            taskStepRepository.saveAll(allSteps);
            taskStepRepository.flush();
        }
    }
}
