package com.codegym.aiplanning.service.daily.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.daily.DailyPlan;
import com.codegym.aiplanning.entity.daily.DailyPlanItem;
import com.codegym.aiplanning.entity.daily.DailyPlanVersion;
import com.codegym.aiplanning.entity.daily.DailyPlanVersionOrigin;
import com.codegym.aiplanning.entity.daily.DailyPlanVersionStatus;
import com.codegym.aiplanning.repository.daily.DailyPlanItemRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanVersionRepository;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.service.daily.DailyPlanPersistenceService;
import com.codegym.aiplanning.service.daily.ai.DailyPlanAiResponse;
import java.time.Instant;
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
    private final AuditLogService auditLogService;

    public DailyPlanPersistenceServiceImpl(
            DailyPlanRepository dailyPlanRepository,
            DailyPlanVersionRepository dailyPlanVersionRepository,
            DailyPlanItemRepository dailyPlanItemRepository,
            AuditLogService auditLogService) {
        this.dailyPlanRepository = dailyPlanRepository;
        this.dailyPlanVersionRepository = dailyPlanVersionRepository;
        this.dailyPlanItemRepository = dailyPlanItemRepository;
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
        List<DailyPlanItem> itemsToSave = new java.util.ArrayList<>();
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
        dailyPlanItemRepository.saveAll(itemsToSave);
        dailyPlanItemRepository.flush();

        auditLogService.logAction(
                userId,
                username,
                AuditEventAction.DAILY_PLAN_VERSION_CREATED,
                "DailyPlanVersion",
                savedDraft.getId().toString());

        return savedDraft;
    }
}
