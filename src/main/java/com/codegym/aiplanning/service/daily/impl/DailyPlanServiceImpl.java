package com.codegym.aiplanning.service.daily.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.daily.dto.AvailableLearningUnitResponse;
import com.codegym.aiplanning.controller.daily.dto.CreateDailyPlanRequest;
import com.codegym.aiplanning.controller.daily.dto.CreateDailyTaskRequest;
import com.codegym.aiplanning.controller.daily.dto.DailyPlanItemResponse;
import com.codegym.aiplanning.controller.daily.dto.DailyPlanResponse;
import com.codegym.aiplanning.controller.daily.dto.DailyPlanSummaryResponse;
import com.codegym.aiplanning.controller.daily.dto.DailyPlanVersionResponse;
import com.codegym.aiplanning.controller.daily.dto.ProgressEntryResponse;
import com.codegym.aiplanning.controller.daily.dto.DailyPlanTaskProgressHistoryResponse;
import com.codegym.aiplanning.controller.daily.dto.RecordPomodoroSessionRequest;
import com.codegym.aiplanning.controller.daily.dto.UpdateDailyTaskRequest;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.ai.AiProviderConfig;
import com.codegym.aiplanning.entity.daily.DailyPlan;
import com.codegym.aiplanning.entity.daily.DailyPlanItem;
import com.codegym.aiplanning.entity.daily.DailyPlanStatus;
import com.codegym.aiplanning.entity.daily.DailyPlanVersion;
import com.codegym.aiplanning.entity.daily.DailyPlanVersionOrigin;
import com.codegym.aiplanning.entity.daily.DailyPlanVersionStatus;
import com.codegym.aiplanning.entity.daily.DailyTaskStatus;
import com.codegym.aiplanning.entity.daily.ProgressEntry;
import com.codegym.aiplanning.entity.daily.ProgressEntryStatus;
import com.codegym.aiplanning.entity.profile.UserProfile;
import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapItem;
import com.codegym.aiplanning.entity.roadmap.RoadmapItemProgress;
import com.codegym.aiplanning.entity.roadmap.RoadmapItemProgressStatus;
import com.codegym.aiplanning.entity.roadmap.RoadmapItemType;
import com.codegym.aiplanning.entity.roadmap.RoadmapStatus;
import com.codegym.aiplanning.repository.daily.DailyPlanItemRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanVersionRepository;
import com.codegym.aiplanning.repository.daily.ProgressEntryRepository;
import com.codegym.aiplanning.repository.profile.UserProfileRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapItemProgressRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapItemRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapRepository;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.service.daily.DailyPlanPersistenceService;
import com.codegym.aiplanning.service.daily.DailyPlanService;
import com.codegym.aiplanning.service.daily.ai.DailyPlanAiGenerator;
import com.codegym.aiplanning.service.daily.ai.DailyPlanAiResponse;
import com.codegym.aiplanning.service.daily.ai.DailyPlanningContext;
import com.codegym.aiplanning.service.daily.ai.PlanningContextBuilder;
import com.codegym.aiplanning.service.evaluation.WeakTopicService;
import com.codegym.aiplanning.service.roadmap.RoadmapProgressService;
import com.codegym.aiplanning.service.roadmap.progress.ProgressHistoryResolver;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyPlanServiceImpl implements DailyPlanService {

    private final DailyPlanRepository dailyPlanRepository;
    private final DailyPlanVersionRepository dailyPlanVersionRepository;
    private final DailyPlanItemRepository dailyPlanItemRepository;
    private final ProgressEntryRepository progressEntryRepository;
    private final UserProfileRepository userProfileRepository;
    private final RoadmapRepository roadmapRepository;
    private final RoadmapItemRepository roadmapItemRepository;
    private final RoadmapItemProgressRepository roadmapItemProgressRepository;
    private final AuditLogService auditLogService;
    private final PlanningContextBuilder contextBuilder;
    private final DailyPlanAiGenerator aiGenerator;
    private final DailyPlanPersistenceService persistenceService;
    private final WeakTopicService weakTopicService;
    private final RoadmapProgressService roadmapProgressService;

    public DailyPlanServiceImpl(
            DailyPlanRepository dailyPlanRepository,
            DailyPlanVersionRepository dailyPlanVersionRepository,
            DailyPlanItemRepository dailyPlanItemRepository,
            ProgressEntryRepository progressEntryRepository,
            UserProfileRepository userProfileRepository,
            RoadmapRepository roadmapRepository,
            RoadmapItemRepository roadmapItemRepository,
            RoadmapItemProgressRepository roadmapItemProgressRepository,
            AuditLogService auditLogService,
            PlanningContextBuilder contextBuilder,
            DailyPlanAiGenerator aiGenerator,
            DailyPlanPersistenceService persistenceService,
            WeakTopicService weakTopicService,
            RoadmapProgressService roadmapProgressService) {
        this.dailyPlanRepository = dailyPlanRepository;
        this.dailyPlanVersionRepository = dailyPlanVersionRepository;
        this.dailyPlanItemRepository = dailyPlanItemRepository;
        this.progressEntryRepository = progressEntryRepository;
        this.userProfileRepository = userProfileRepository;
        this.roadmapRepository = roadmapRepository;
        this.roadmapItemRepository = roadmapItemRepository;
        this.roadmapItemProgressRepository = roadmapItemProgressRepository;
        this.auditLogService = auditLogService;
        this.contextBuilder = contextBuilder;
        this.aiGenerator = aiGenerator;
        this.persistenceService = persistenceService;
        this.weakTopicService = weakTopicService;
        this.roadmapProgressService = roadmapProgressService;
    }

    @Override
    @Transactional
    public DailyPlanResponse createDailyPlan(CreateDailyPlanRequest request, Jwt actorJwt) {
        UUID userId = extractUserId(actorJwt);
        String username = extractUsername(actorJwt);

        if (dailyPlanRepository.findByUserIdAndPlanDate(userId, request.planDate()).isPresent()) {
            throw new BusinessException(
                    ErrorCode.DAILY_PLAN_EXISTS,
                    "A daily plan already exists for date: " + request.planDate());
        }

        UUID resolvedRoadmapId = request.roadmapId();
        Roadmap roadmap = null;
        if (resolvedRoadmapId != null) {
            roadmap = roadmapRepository.findByIdAndOwnerId(resolvedRoadmapId, userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Roadmap not found"));
            if (roadmap.getStatus() == RoadmapStatus.ONBOARDING || roadmap.getStatus() == RoadmapStatus.ARCHIVED) {
                throw new BusinessException(ErrorCode.INVALID_PLAN_TRANSITION, "Roadmap is not in a valid state");
            }
        } else {
            List<Roadmap> activeRoadmaps = roadmapRepository.findAllByOwnerIdOrderByUpdatedAtDesc(userId).stream()
                    .filter(r -> r.getStatus() == RoadmapStatus.ACTIVE)
                    .toList();
            if (!activeRoadmaps.isEmpty()) {
                roadmap = activeRoadmaps.get(0);
                resolvedRoadmapId = roadmap.getId();
            }
        }

        String timeZone = getUserTimeZone(userId);
        int availableMinutes = request.availableMinutes() != null ? request.availableMinutes() : 60;

        DailyPlan plan = DailyPlan.create(userId, request.planDate(), timeZone, resolvedRoadmapId);
        DailyPlan savedPlan = dailyPlanRepository.save(plan);

        DailyPlanVersion version = DailyPlanVersion.create(
                savedPlan.getId(),
                1,
                DailyPlanVersionOrigin.MANUAL,
                availableMinutes,
                0);
        DailyPlanVersion savedVersion = dailyPlanVersionRepository.save(version);

        auditLogService.logAction(
                userId,
                username,
                AuditEventAction.DAILY_PLAN_CREATED,
                "DailyPlan",
                savedPlan.getId().toString());

        return DailyPlanResponse.of(
                savedPlan,
                savedVersion.getId(),
                savedVersion.getAvailableMinutes(),
                savedVersion.getTotalPlannedMinutes(),
                List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public DailyPlanResponse getTodayPlan(Jwt actorJwt) {
        UUID userId = extractUserId(actorJwt);
        String timeZone = getUserTimeZone(userId);
        LocalDate today = LocalDate.now(ZoneId.of(timeZone));

        DailyPlan plan = dailyPlanRepository
                .findByUserIdAndPlanDate(userId, today)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.DAILY_PLAN_NOT_FOUND,
                        "No daily plan found for today (" + today + "). Please create a new daily plan."));

        return buildDailyPlanResponse(plan);
    }

    @Override
    @Transactional(readOnly = true)
    public DailyPlanResponse getPlanById(UUID planId, Jwt actorJwt) {
        UUID userId = extractUserId(actorJwt);
        DailyPlan plan = requirePlanForUser(planId, userId);
        return buildDailyPlanResponse(plan);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DailyPlanSummaryResponse> getUserDailyPlans(
            DailyPlanStatus status,
            UUID roadmapId,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable,
            Jwt actorJwt) {
        UUID userId = extractUserId(actorJwt);
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "The from date must not be after the to date.");
        }
        Page<DailyPlan> plans = dailyPlanRepository.searchOwned(
                userId, status, roadmapId, fromDate, toDate, pageable);
        if (plans.isEmpty()) {
            return Page.empty(pageable);
        }

        List<DailyPlanVersion> versions = dailyPlanVersionRepository
                .findCurrentVersionsByDailyPlanIds(
                        plans.getContent().stream().map(DailyPlan::getId).toList());
        Map<UUID, DailyPlanVersion> versionByPlanId = new HashMap<>();
        for (DailyPlanVersion version : versions) {
            versionByPlanId.put(version.getDailyPlanId(), version);
        }

        Map<UUID, List<DailyPlanItem>> itemsByVersionId = new HashMap<>();
        if (!versions.isEmpty()) {
            List<DailyPlanItem> items = dailyPlanItemRepository.findByDailyPlanVersionIds(
                    versions.stream().map(DailyPlanVersion::getId).toList());
            for (DailyPlanItem item : items) {
                itemsByVersionId
                        .computeIfAbsent(
                                item.getDailyPlanVersionId(), ignored -> new ArrayList<>())
                        .add(item);
            }
        }

        return plans.map(plan -> {
            DailyPlanVersion version = versionByPlanId.get(plan.getId());
            List<DailyPlanItem> items = version == null
                    ? List.of()
                    : itemsByVersionId.getOrDefault(version.getId(), List.of());
            return DailyPlanSummaryResponse.from(plan, version, items);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailyPlanVersionResponse> getVersions(UUID planId, Jwt actorJwt) {
        UUID userId = extractUserId(actorJwt);
        DailyPlan plan = requirePlanForUser(planId, userId);
        List<DailyPlanVersion> versions =
                dailyPlanVersionRepository.findByDailyPlanIdOrderByVersionNumberDesc(plan.getId());
        if (versions.isEmpty()) {
            return List.of();
        }

        List<DailyPlanItem> allItems = dailyPlanItemRepository.findByDailyPlanVersionIds(
                versions.stream().map(DailyPlanVersion::getId).toList());
        List<DailyPlanItemResponse> enrichedItems = enrichTaskResponses(allItems, userId);
        Map<UUID, List<DailyPlanItemResponse>> itemsByVersionId = new HashMap<>();
        for (DailyPlanItemResponse item : enrichedItems) {
            itemsByVersionId
                    .computeIfAbsent(
                            item.versionId(), ignored -> new ArrayList<>())
                    .add(item);
        }
        return versions.stream()
                .map(version -> DailyPlanVersionResponse.of(
                        version,
                        itemsByVersionId.getOrDefault(version.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DailyPlanVersionResponse getVersion(
            UUID planId, UUID versionId, Jwt actorJwt) {
        UUID userId = extractUserId(actorJwt);
        DailyPlan plan = requirePlanForUser(planId, userId);
        DailyPlanVersion version = requireVersion(plan.getId(), versionId);
        return versionResponse(version, userId);
    }

    @Override
    @Transactional
    public DailyPlanVersionResponse createDraftVersion(UUID planId, Jwt actorJwt) {
        UUID userId = extractUserId(actorJwt);
        String username = extractUsername(actorJwt);
        DailyPlan plan = requirePlanForUserForUpdate(planId, userId);
        if (plan.getStatus() != DailyPlanStatus.READY) {
            throw new BusinessException(
                    ErrorCode.DAILY_PLAN_LOCKED,
                    "A new draft can only be created for a READY Daily Plan.");
        }
        if (dailyPlanVersionRepository
                .findByDailyPlanIdAndStatus(planId, DailyPlanVersionStatus.DRAFT)
                .isPresent()) {
            throw new BusinessException(
                    ErrorCode.DAILY_PLAN_DRAFT_EXISTS,
                    "This Daily Plan already has an editable draft version.");
        }

        DailyPlanVersion active = requireActiveVersion(plan);
        int nextNumber = dailyPlanVersionRepository
                        .findTopByDailyPlanIdOrderByVersionNumberDesc(planId)
                        .map(existing -> existing.getVersionNumber() + 1)
                        .orElse(1);
        DailyPlanVersion draft = dailyPlanVersionRepository.saveAndFlush(
                DailyPlanVersion.create(
                        planId,
                        nextNumber,
                        DailyPlanVersionOrigin.USER_EDITED,
                        active.getAvailableMinutes(),
                        active.getTotalPlannedMinutes()));

        List<DailyPlanItem> copies = dailyPlanItemRepository
                .findByDailyPlanVersionIdOrderByOrderIndexAsc(active.getId())
                .stream()
                .map(item -> DailyPlanItem.create(
                        draft.getId(),
                        item.getCategory(),
                        item.getTitle(),
                        item.getDescription(),
                        item.getPlannedMinutes(),
                        item.getOrderIndex(),
                        item.getRoadmapItemId()))
                .toList();
        List<DailyPlanItem> savedCopies = dailyPlanItemRepository.saveAll(copies);
        dailyPlanItemRepository.flush();

        auditLogService.logAction(
                userId,
                username,
                AuditEventAction.DAILY_PLAN_VERSION_CREATED,
                "DailyPlanVersion",
                draft.getId().toString());
        return DailyPlanVersionResponse.of(draft, enrichTaskResponses(savedCopies, userId));
    }

    @Override
    public DailyPlanVersionResponse generateAiDraftVersion(
            UUID planId, String idempotencyKey, Jwt actorJwt) {
        UUID userId = extractUserId(actorJwt);
        String username = extractUsername(actorJwt);
        String normalizedRequestKey = normalizeIdempotencyKey(idempotencyKey);
        return generateAiDraft(
                planId, userId, username, normalizedRequestKey, null);
    }

    @Override
    public DailyPlanVersionResponse generateAiDraftVersionWithProviderConfig(
            UUID planId,
            UUID ownerId,
            String ownerEmail,
            String generationRequestKey,
            AiProviderConfig providerConfig) {
        if (providerConfig == null) {
            throw new BusinessException(
                    ErrorCode.AI_PROVIDER_INVALID_CONFIGURATION,
                    "AI provider configuration is required.");
        }
        return generateAiDraft(
                planId,
                ownerId,
                ownerEmail,
                normalizeIdempotencyKey(generationRequestKey),
                providerConfig);
    }

    private DailyPlanVersionResponse generateAiDraft(
            UUID planId,
            UUID userId,
            String username,
            String normalizedRequestKey,
            AiProviderConfig providerConfig) {

        DailyPlan plan = requirePlanForUser(planId, userId);
        if (plan.getStatus() != DailyPlanStatus.READY && plan.getStatus() != DailyPlanStatus.DRAFT) {
            throw new BusinessException(
                    ErrorCode.DAILY_PLAN_LOCKED,
                    "Cannot generate AI plan for a Daily Plan that is already in progress or completed.");
        }

        if (normalizedRequestKey != null) {
            DailyPlanVersion existing = dailyPlanVersionRepository
                    .findByDailyPlanIdAndGenerationRequestKey(planId, normalizedRequestKey)
                    .orElse(null);
            if (existing != null) {
                return versionResponse(existing, userId);
            }
        }

        DailyPlanningContext context = contextBuilder.buildContext(planId, userId);
        DailyPlanAiGenerator.GeneratedDailyPlan generated = providerConfig == null
                ? aiGenerator.generate(context)
                : aiGenerator.generate(context, providerConfig);
        DailyPlanAiResponse response = generated.response();
        int totalPlannedMinutes = response.items().stream()
                .mapToInt(DailyPlanAiResponse.AiPlanItemDto::plannedMinutes)
                .sum();

        DailyPlanVersion savedDraft = persistenceService.persistAiGeneratedDraft(
                planId,
                userId,
                username,
                totalPlannedMinutes,
                buildAiExplanation(response),
                generated.requiresUserDecision(),
                normalizedRequestKey,
                response.items());

        return versionResponse(savedDraft, userId);
    }

    private String buildAiExplanation(DailyPlanAiResponse response) {
        if (response.adjustments().isEmpty()) {
            return response.summary().trim();
        }
        String adjustmentSummary = response.adjustments().stream()
                .map(adjustment -> adjustment.action()
                        + ": "
                        + adjustment.title().trim()
                        + " — "
                        + adjustment.reason().trim())
                .collect(java.util.stream.Collectors.joining("\n"));
        return response.summary().trim() + "\n\nĐề xuất cần người dùng xem xét:\n" + adjustmentSummary;
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

    private UUID resolveLearningUnitId(UUID learningUnitId, UUID legacyRoadmapItemId) {
        if (learningUnitId != null
                && legacyRoadmapItemId != null
                && !learningUnitId.equals(legacyRoadmapItemId)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "learningUnitId and the deprecated roadmapItemId alias must match.");
        }
        return learningUnitId != null ? learningUnitId : legacyRoadmapItemId;
    }

    @Override
    @Transactional
    public DailyPlanItemResponse addTaskToPlan(
            UUID planId,
            UUID versionId,
            CreateDailyTaskRequest request,
            Jwt actorJwt) {
        UUID userId = extractUserId(actorJwt);
        String username = extractUsername(actorJwt);

        DailyPlan plan = requirePlanForUser(planId, userId);
        DailyPlanVersion version = requireEditableDraftVersion(plan, versionId);

        List<DailyPlanItem> existingItems = dailyPlanItemRepository.findByDailyPlanVersionIdOrderByOrderIndexAsc(version.getId());
        int orderIndex = existingItems.size();
        int plannedMinutes = request.plannedMinutes() != null ? request.plannedMinutes() : 30;

        UUID roadmapItemId = resolveLearningUnitId(
                request.learningUnitId(), request.roadmapItemId());
        if (roadmapItemId != null) {
            RoadmapItem roadmapItem = roadmapItemRepository.findOwnedById(roadmapItemId, userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Roadmap item not found"));
            Roadmap roadmap = roadmapItem.getRoadmapVersion().getRoadmap();
            if (roadmap.getStatus() != RoadmapStatus.ACTIVE
                    || roadmap.getActiveVersionId() == null
                    || !roadmap.getActiveVersionId().equals(
                            roadmapItem.getRoadmapVersion().getId())) {
                throw new BusinessException(
                        ErrorCode.INVALID_PLAN_TRANSITION,
                        "A Roadmap-backed task must reference the ACTIVE RoadmapVersion.");
            }
            if (roadmapItem.getItemType()
                    != com.codegym.aiplanning.entity.roadmap.RoadmapItemType.LEARNING_UNIT) {
                throw new BusinessException(
                        ErrorCode.INVALID_PLAN_TRANSITION,
                        "A Roadmap-backed Daily Plan task must reference a Learning Unit.");
            }
            if (plan.getRoadmapId() == null) {
                plan.setRoadmapId(roadmap.getId());
                dailyPlanRepository.save(plan);
            } else if (!plan.getRoadmapId().equals(roadmap.getId())) {
                throw new BusinessException(ErrorCode.INVALID_PLAN_TRANSITION, "Roadmap item does not belong to the plan's roadmap");
            }
        }

        DailyPlanItem item = DailyPlanItem.create(
                version.getId(),
                request.category(),
                request.title().trim(),
                request.description() != null ? request.description().trim() : null,
                plannedMinutes,
                orderIndex,
                roadmapItemId);

        DailyPlanItem savedItem = dailyPlanItemRepository.save(item);

        int newTotalMinutes = version.getTotalPlannedMinutes() + plannedMinutes;
        version.updateTotalPlannedMinutes(newTotalMinutes);
        dailyPlanVersionRepository.save(version);

        auditLogService.logAction(
                userId,
                username,
                AuditEventAction.DAILY_PLAN_UPDATED,
                "DailyPlanItem",
                savedItem.getId().toString());

        return enrichTaskResponse(savedItem, userId);
    }

    @Override
    @Transactional
    public DailyPlanVersionResponse updateTask(
            UUID planId,
            UUID versionId,
            UUID itemId,
            UpdateDailyTaskRequest request,
            Jwt actorJwt) {
        UUID userId = extractUserId(actorJwt);
        String username = extractUsername(actorJwt);
        DailyPlan plan = requirePlanForUserForUpdate(planId, userId);
        DailyPlanVersion version = requireEditableDraftVersion(plan, versionId);
        List<DailyPlanItem> items = new ArrayList<>(dailyPlanItemRepository
                .findByDailyPlanVersionIdOrderByOrderIndexAsc(versionId));
        DailyPlanItem item = items.stream()
                .filter(candidate -> candidate.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.DAILY_PLAN_ITEM_NOT_FOUND,
                        "Daily plan item not found."));

        items.remove(item);
        int targetIndex = Math.min(request.orderIndex(), items.size());

        UUID roadmapItemId = item.getRoadmapItemId();
        if (Boolean.TRUE.equals(request.clearLearningUnit())) {
            if (request.learningUnitId() != null || request.roadmapItemId() != null) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_FAILED,
                        "A task cannot clear and replace its Learning Unit in the same request.");
            }
            roadmapItemId = null;
        }
        UUID requestedLearningUnitId = resolveLearningUnitId(
                request.learningUnitId(), request.roadmapItemId());
        if (requestedLearningUnitId != null) {
            UUID targetRoadmapItemId = requestedLearningUnitId;
            RoadmapItem roadmapItem = roadmapItemRepository.findOwnedById(targetRoadmapItemId, userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Roadmap item not found"));
            Roadmap roadmap = roadmapItem.getRoadmapVersion().getRoadmap();
            if (roadmap.getStatus() != RoadmapStatus.ACTIVE
                    || roadmap.getActiveVersionId() == null
                    || !roadmap.getActiveVersionId().equals(
                            roadmapItem.getRoadmapVersion().getId())) {
                throw new BusinessException(
                        ErrorCode.INVALID_PLAN_TRANSITION,
                        "A Roadmap-backed task must reference the ACTIVE RoadmapVersion.");
            }
            if (roadmapItem.getItemType()
                    != com.codegym.aiplanning.entity.roadmap.RoadmapItemType.LEARNING_UNIT) {
                throw new BusinessException(
                        ErrorCode.INVALID_PLAN_TRANSITION,
                        "A Roadmap-backed Daily Plan task must reference a Learning Unit.");
            }
            if (plan.getRoadmapId() == null) {
                plan.setRoadmapId(roadmap.getId());
                dailyPlanRepository.save(plan);
            } else if (!plan.getRoadmapId().equals(roadmap.getId())) {
                throw new BusinessException(ErrorCode.INVALID_PLAN_TRANSITION, "Roadmap item does not belong to the plan's roadmap");
            }
            roadmapItemId = targetRoadmapItemId;
        }

        item.updateDraftDetails(
                request.category(),
                request.title().trim(),
                normalizeOptional(request.description()),
                request.plannedMinutes(),
                targetIndex,
                roadmapItemId);
        items.add(targetIndex, item);

        int totalPlannedMinutes = 0;
        for (int index = 0; index < items.size(); index++) {
            DailyPlanItem current = items.get(index);
            current.updateDraftDetails(
                    current.getCategory(),
                    current.getTitle(),
                    current.getDescription(),
                    current.getPlannedMinutes(),
                    index);
            totalPlannedMinutes += current.getPlannedMinutes();
        }
        dailyPlanItemRepository.saveAll(items);
        version.updateTotalPlannedMinutes(totalPlannedMinutes);
        dailyPlanVersionRepository.saveAndFlush(version);

        auditLogService.logAction(
                userId,
                username,
                AuditEventAction.DAILY_PLAN_UPDATED,
                "DailyPlanItem",
                itemId.toString());
        return DailyPlanVersionResponse.of(version, enrichTaskResponses(items, userId));
    }

    @Override
    @Transactional
    public DailyPlanResponse activateVersion(UUID planId, UUID versionId, Jwt actorJwt) {
        UUID userId = extractUserId(actorJwt);
        String username = extractUsername(actorJwt);

        DailyPlan plan = requirePlanForUserForUpdate(planId, userId);
        if (plan.getStatus() != DailyPlanStatus.DRAFT
                && plan.getStatus() != DailyPlanStatus.READY) {
            throw new BusinessException(
                    ErrorCode.DAILY_PLAN_LOCKED,
                    "A Daily Plan version cannot be activated after execution starts.");
        }

        DailyPlanVersion version = dailyPlanVersionRepository
                .findByIdAndDailyPlanIdForUpdate(versionId, planId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.DAILY_PLAN_VERSION_NOT_FOUND,
                        "Daily Plan version not found: " + versionId));
        if (!version.isDraft()) {
            throw new BusinessException(
                    ErrorCode.INVALID_PLAN_TRANSITION,
                    "Only a DRAFT Daily Plan version can be activated.");
        }

        List<DailyPlanItem> items = dailyPlanItemRepository
                .findByDailyPlanVersionIdOrderByOrderIndexAsc(version.getId());
        if (items.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.INVALID_PLAN_TRANSITION,
                    "Cannot activate a version with no tasks.");
        }
        validateRoadmapBackedTasks(plan, items, userId);

        Instant now = Instant.now();
        if (plan.getActiveVersionId() != null) {
            DailyPlanVersion previousActive = dailyPlanVersionRepository
                    .findByIdAndDailyPlanIdForUpdate(plan.getActiveVersionId(), planId)
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.INTERNAL_ERROR,
                            "The active Daily Plan version is missing."));
            previousActive.supersede(now);
            dailyPlanVersionRepository.saveAndFlush(previousActive);
        }

        version.activate(now);
        dailyPlanVersionRepository.saveAndFlush(version);
        plan.activateVersion(versionId);
        dailyPlanRepository.save(plan);

        items.stream()
                .filter(item -> item.getCategory()
                        == com.codegym.aiplanning.entity.daily.DailyTaskCategory.REVIEW)
                .map(DailyPlanItem::getRoadmapItemId)
                .filter(java.util.Objects::nonNull)
                .forEach(roadmapItemId -> weakTopicService.markInReviewByRoadmapItem(
                        userId,
                        roadmapItemId));

        auditLogService.logAction(
                userId,
                username,
                AuditEventAction.DAILY_PLAN_VERSION_ACTIVATED,
                "DailyPlanVersion",
                versionId.toString());

        return buildDailyPlanResponse(plan, version, items);
    }

    @Override
    @Transactional
    public DailyPlanItemResponse recordProgress(
            UUID planId,
            UUID itemId,
            com.codegym.aiplanning.controller.daily.dto.RecordProgressRequest request,
            String idempotencyKey,
            Jwt actorJwt) {
        UUID userId = extractUserId(actorJwt);
        String username = extractUsername(actorJwt);
        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);

        DailyPlan plan = requirePlanForUserForUpdate(planId, userId);
        DailyPlanVersion version = requireActiveVersion(plan);

        if (plan.getStatus() == DailyPlanStatus.READY) {
            plan.startExecution(Instant.now());
            dailyPlanRepository.save(plan);
        } else if (plan.getStatus() != DailyPlanStatus.IN_PROGRESS) {
            throw new BusinessException(
                    ErrorCode.DAILY_PLAN_LOCKED,
                    "Progress can only be recorded for a READY or IN_PROGRESS Daily Plan.");
        }

        DailyPlanItem item = dailyPlanItemRepository
                .findById(itemId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.DAILY_PLAN_ITEM_NOT_FOUND,
                        "Task item not found: " + itemId));

        if (!item.getDailyPlanVersionId().equals(version.getId())) {
            throw new BusinessException(
                    ErrorCode.DAILY_PLAN_ITEM_NOT_FOUND,
                    "Task item does not belong to active version of this daily plan.");
        }
        requireNotRemoved(item);

        if (normalizedIdempotencyKey != null) {
            ProgressEntry existing = progressEntryRepository
                    .findByUserIdAndIdempotencyKey(userId, normalizedIdempotencyKey)
                    .orElse(null);
            if (existing != null) {
                if (!itemId.equals(existing.getDailyPlanItemId())) {
                    throw new BusinessException(
                            ErrorCode.CONFLICT,
                            "Idempotency-Key was already used for another progress operation.");
                }
                return enrichTaskResponse(item, userId);
            }
        }

        RoadmapItem learningUnit = resolveLearningUnit(item, userId);

        DailyTaskStatus taskStatus = toDailyTaskStatus(request.status());
        item.updateStatus(taskStatus);
        DailyPlanItem savedItem = dailyPlanItemRepository.save(item);

        int actualMinutes = request.actualMinutes() != null
                ? request.actualMinutes()
                : item.getPlannedMinutes();
        int percentage = taskStatus.completionPercentage();

        ProgressEntry entry = ProgressEntry.create(
                userId,
                itemId,
                learningUnit != null ? learningUnit.getRoadmapVersion().getId() : null,
                learningUnit != null ? learningUnit.getId() : null,
                request.status(),
                actualMinutes,
                percentage,
                request.actualResult(),
                request.difficulty(),
                request.understandingRating(),
                request.note(),
                null,
                normalizedIdempotencyKey);
        ProgressEntry savedEntry = progressEntryRepository.save(entry);
        roadmapProgressService.recordOutcome(
                userId,
                item.getRoadmapItemId(),
                savedEntry,
                request.status());

        auditLogService.logAction(
                userId,
                username,
                AuditEventAction.PROGRESS_RECORDED,
                "DailyPlanItem",
                savedItem.getId().toString());

        return enrichTaskResponse(savedItem, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProgressEntryResponse> getProgressHistory(
            UUID planId, UUID itemId, Jwt actorJwt) {
        UUID userId = extractUserId(actorJwt);
        requireTaskInPlan(planId, itemId, userId);
        return progressEntryRepository
                .findByUserIdAndDailyPlanItemIdOrderByRecordedAtDesc(
                        userId, itemId)
                .stream()
                .map(ProgressEntryResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailyPlanTaskProgressHistoryResponse> getPlanProgressHistory(
            UUID planId, Jwt actorJwt) {
        UUID userId = extractUserId(actorJwt);
        requirePlanForUser(planId, userId);

        List<UUID> versionIds = dailyPlanVersionRepository
                .findByDailyPlanIdOrderByVersionNumberDesc(planId)
                .stream()
                .map(DailyPlanVersion::getId)
                .toList();
        if (versionIds.isEmpty()) {
            return List.of();
        }

        List<DailyPlanItem> items = dailyPlanItemRepository
                .findIncludingRemovedByDailyPlanVersionIds(versionIds);
        if (items.isEmpty()) {
            return List.of();
        }

        List<UUID> itemIds = items.stream().map(DailyPlanItem::getId).toList();
        Map<UUID, List<ProgressEntryResponse>> entriesByItemId = progressEntryRepository
                .findByUserIdAndDailyPlanItemIdInOrderByRecordedAtDesc(userId, itemIds)
                .stream()
                .map(ProgressEntryResponse::from)
                .collect(Collectors.groupingBy(ProgressEntryResponse::dailyPlanItemId));

        return items.stream()
                .filter(item -> entriesByItemId.containsKey(item.getId()))
                .map(item -> new DailyPlanTaskProgressHistoryResponse(
                        item.getId(),
                        item.getDailyPlanVersionId(),
                        item.getTitle(),
                        item.getRemovedAt(),
                        entriesByItemId.get(item.getId())))
                .toList();
    }

    @Override
    @Transactional
    public ProgressEntryResponse correctProgress(
            UUID planId,
            UUID itemId,
            UUID progressEntryId,
            com.codegym.aiplanning.controller.daily.dto.RecordProgressRequest request,
            String idempotencyKey,
            Jwt actorJwt) {
        UUID userId = extractUserId(actorJwt);
        String username = extractUsername(actorJwt);
        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        requirePlanForUserForUpdate(planId, userId);
        DailyPlanItem item = requireTaskInPlan(planId, itemId, userId);

        if (normalizedIdempotencyKey != null) {
            ProgressEntry replay = progressEntryRepository
                    .findByUserIdAndIdempotencyKey(userId, normalizedIdempotencyKey)
                    .orElse(null);
            if (replay != null) {
                if (!itemId.equals(replay.getDailyPlanItemId())
                        || !progressEntryId.equals(replay.getSupersedesEntryId())) {
                    throw new BusinessException(
                            ErrorCode.CONFLICT,
                            "Idempotency-Key was already used for another progress operation.");
                }
                return ProgressEntryResponse.from(replay);
            }
        }

        ProgressEntry original = progressEntryRepository
                .findByIdAndUserId(progressEntryId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Progress entry not found."));
        if (!itemId.equals(original.getDailyPlanItemId())) {
            throw new BusinessException(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    "Progress entry does not belong to this task.");
        }
        if (progressEntryRepository.existsBySupersedesEntryId(progressEntryId)) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "This progress entry has already been corrected.");
        }

        RoadmapItem learningUnit = resolveCorrectionLearningUnit(
                original, userId);
        DailyTaskStatus correctedTaskStatus = toDailyTaskStatus(request.status());

        ProgressEntry correction = ProgressEntry.create(
                userId,
                itemId,
                learningUnit != null ? learningUnit.getRoadmapVersion().getId() : null,
                learningUnit != null ? learningUnit.getId() : null,
                request.status(),
                request.actualMinutes() != null
                        ? request.actualMinutes()
                        : item.getPlannedMinutes(),
                correctedTaskStatus.completionPercentage(),
                request.actualResult(),
                request.difficulty(),
                request.understandingRating(),
                request.note(),
                original.getId(),
                normalizedIdempotencyKey);
        ProgressEntry savedCorrection = progressEntryRepository.save(correction);
        List<ProgressEntry> taskHistory = progressEntryRepository
                .findByUserIdAndDailyPlanItemIdOrderByRecordedAtDesc(userId, itemId);
        List<ProgressEntry> effectiveTaskHistory = ProgressHistoryResolver
                .effectiveEntries(taskHistory);
        ProgressEntry latestEffectiveEntry = effectiveTaskHistory.get(
                effectiveTaskHistory.size() - 1);
        item.updateStatus(toDailyTaskStatus(latestEffectiveEntry.getStatus()));
        dailyPlanItemRepository.save(item);
        if (learningUnit != null) {
            roadmapProgressService.correctOutcome(
                    userId,
                    learningUnit.getId(),
                    savedCorrection,
                    request.status());
        }

        auditLogService.logAction(
                userId,
                username,
                AuditEventAction.PROGRESS_RECORDED,
                "ProgressEntryCorrection",
                savedCorrection.getId().toString());
        return ProgressEntryResponse.from(savedCorrection);
    }

    @Override
    @Transactional
    public DailyPlanItemResponse recordPomodoroSession(UUID planId, UUID itemId, RecordPomodoroSessionRequest request, Jwt actorJwt) {
        UUID userId = extractUserId(actorJwt);
        String username = extractUsername(actorJwt);

        DailyPlan plan = requirePlanForUserForUpdate(planId, userId);
        DailyPlanVersion version = requireActiveVersion(plan);

        if (plan.getStatus() == DailyPlanStatus.READY) {
            plan.startExecution(Instant.now());
            dailyPlanRepository.save(plan);
        } else if (plan.getStatus() != DailyPlanStatus.IN_PROGRESS) {
            throw new BusinessException(
                    ErrorCode.DAILY_PLAN_LOCKED,
                    "Pomodoro progress can only be recorded for a READY or IN_PROGRESS Daily Plan.");
        }

        DailyPlanItem item = dailyPlanItemRepository
                .findById(itemId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.DAILY_PLAN_ITEM_NOT_FOUND,
                        "Task item not found: " + itemId));

        if (!item.getDailyPlanVersionId().equals(version.getId())) {
            throw new BusinessException(
                    ErrorCode.DAILY_PLAN_ITEM_NOT_FOUND,
                    "Task item does not belong to active version of this daily plan.");
        }
        requireNotRemoved(item);

        if (item.getStatus() == DailyTaskStatus.NOT_STARTED) {
            item.updateStatus(DailyTaskStatus.IN_PROGRESS);
            dailyPlanItemRepository.save(item);
        }

        int pomodoroMinutes = request.completedMinutes() != null ? request.completedMinutes() : 25;

        com.codegym.aiplanning.entity.daily.ProgressEntryStatus progressStatus = 
                item.getStatus() == DailyTaskStatus.COMPLETED 
                        ? com.codegym.aiplanning.entity.daily.ProgressEntryStatus.COMPLETED 
                        : com.codegym.aiplanning.entity.daily.ProgressEntryStatus.PARTIALLY_COMPLETED;

        RoadmapItem learningUnit = resolveLearningUnit(item, userId);
        ProgressEntry entry = ProgressEntry.create(
                userId,
                itemId,
                learningUnit != null ? learningUnit.getRoadmapVersion().getId() : null,
                learningUnit != null ? learningUnit.getId() : null,
                progressStatus,
                pomodoroMinutes,
                item.getStatus() == DailyTaskStatus.COMPLETED ? 100 : 50,
                "Pomodoro session",
                null,
                null,
                null,
                null,
                null);
        ProgressEntry savedEntry = progressEntryRepository.save(entry);
        if (learningUnit != null) {
            roadmapProgressService.recordOutcome(
                    userId,
                    learningUnit.getId(),
                    savedEntry,
                    progressStatus);
        }

        auditLogService.logAction(
                userId,
                username,
                AuditEventAction.PROGRESS_RECORDED,
                "DailyPlanItem",
                item.getId().toString());

        return enrichTaskResponse(item, userId);
    }

    @Override
    @Transactional
    public DailyPlanVersionResponse deleteTask(
            UUID planId, UUID versionId, UUID itemId, Jwt actorJwt) {
        UUID userId = extractUserId(actorJwt);
        String username = extractUsername(actorJwt);

        DailyPlan plan = requirePlanForUser(planId, userId);
        DailyPlanVersion version = requireEditableDraftVersion(plan, versionId);

        DailyPlanItem item = dailyPlanItemRepository
                .findById(itemId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.DAILY_PLAN_ITEM_NOT_FOUND,
                        "Task item not found: " + itemId));

        if (!item.getDailyPlanVersionId().equals(version.getId())) {
            throw new BusinessException(
                    ErrorCode.DAILY_PLAN_ITEM_NOT_FOUND,
                    "Task item does not belong to this Daily Plan version.");
        }

        if (item.isRemoved()) {
            throw new BusinessException(
                    ErrorCode.DAILY_PLAN_ITEM_NOT_FOUND,
                    "Task item not found: " + itemId);
        }

        item.remove(Instant.now());
        dailyPlanItemRepository.saveAndFlush(item);

        List<DailyPlanItem> remainingItems = dailyPlanItemRepository
                .findByDailyPlanVersionIdOrderByOrderIndexAsc(versionId);
        int newTotalMinutes = 0;
        for (int index = 0; index < remainingItems.size(); index++) {
            DailyPlanItem remaining = remainingItems.get(index);
            remaining.updateDraftDetails(
                    remaining.getCategory(),
                    remaining.getTitle(),
                    remaining.getDescription(),
                    remaining.getPlannedMinutes(),
                    index,
                    remaining.getRoadmapItemId());
            newTotalMinutes += remaining.getPlannedMinutes();
        }
        dailyPlanItemRepository.saveAll(remainingItems);
        version.updateTotalPlannedMinutes(newTotalMinutes);
        dailyPlanVersionRepository.save(version);

        auditLogService.logAction(
                userId,
                username,
                AuditEventAction.DAILY_PLAN_UPDATED,
                "DailyPlanItem",
                itemId.toString());

        return DailyPlanVersionResponse.of(version, enrichTaskResponses(remainingItems, userId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AvailableLearningUnitResponse> getAvailableLearningUnits(UUID planId, Jwt actorJwt) {
        UUID userId = extractUserId(actorJwt);
        DailyPlan plan = requirePlanForUser(planId, userId);
        if (plan.getRoadmapId() == null) {
            return List.of();
        }

        Roadmap roadmap = roadmapRepository.findByIdAndOwnerId(plan.getRoadmapId(), userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, "Roadmap not found."));

        if (roadmap.getStatus() != RoadmapStatus.ACTIVE || roadmap.getActiveVersionId() == null) {
            return List.of();
        }

        UUID versionId = roadmap.getActiveVersionId();
        List<RoadmapItem> allItems = roadmapItemRepository.findAllByRoadmapVersionIds(List.of(versionId));
        if (allItems.isEmpty()) {
            return List.of();
        }

        Map<UUID, RoadmapItemProgress> progressByItemId = roadmapItemProgressRepository
                .findByUserIdAndRoadmapVersionId(userId, versionId).stream()
                .collect(Collectors.toMap(
                        RoadmapItemProgress::getRoadmapItemId,
                        Function.identity(),
                        (existing, replacing) -> existing));

        return allItems.stream()
                .filter(item -> item.getItemType() == RoadmapItemType.LEARNING_UNIT)
                .sorted(Comparator
                        .comparingInt(this::milestoneOrderOfLearningUnit)
                        .thenComparingInt(this::topicOrderOfLearningUnit)
                        .thenComparingInt(RoadmapItem::getOrderIndex)
                        .thenComparing(RoadmapItem::getId))
                .map(unit -> {
                    RoadmapItem topic = unit.getParent();
                    RoadmapItem milestone = (topic != null) ? topic.getParent() : null;
                    RoadmapItemProgress progress = progressByItemId.get(unit.getId());
                    RoadmapItemProgressStatus status = progress != null
                            ? progress.getStatus()
                            : RoadmapItemProgressStatus.NOT_STARTED;

                    return new AvailableLearningUnitResponse(
                            unit.getId(),
                            unit.getTitle(),
                            unit.getDescription(),
                            unit.getEstimatedMinutes(),
                            unit.getOrderIndex(),
                            topic != null ? topic.getId() : null,
                            topic != null ? topic.getTitle() : null,
                            milestone != null ? milestone.getId() : null,
                            milestone != null ? milestone.getTitle() : null,
                            status,
                            progress != null ? progress.getLatestOutcome() : null
                    );
                })
                .toList();
    }

    private int milestoneOrderOfLearningUnit(RoadmapItem learningUnit) {
        RoadmapItem topic = learningUnit.getParent();
        RoadmapItem milestone = topic != null ? topic.getParent() : null;
        return milestone != null ? milestone.getOrderIndex() : Integer.MAX_VALUE;
    }

    private int topicOrderOfLearningUnit(RoadmapItem learningUnit) {
        RoadmapItem topic = learningUnit.getParent();
        return topic != null ? topic.getOrderIndex() : Integer.MAX_VALUE;
    }

    private List<DailyPlanItemResponse> enrichTaskResponses(List<DailyPlanItem> items, UUID userId) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        Set<UUID> roadmapItemIds = items.stream()
                .map(DailyPlanItem::getRoadmapItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, RoadmapItem> roadmapItemMap = roadmapItemIds.isEmpty()
                ? Map.of()
                : roadmapItemRepository.findAllOwnedByIdsWithParent(roadmapItemIds, userId).stream()
                        .collect(Collectors.toMap(RoadmapItem::getId, Function.identity(), (existing, replacing) -> existing));

        return items.stream()
                .map(item -> {
                    RoadmapItem roadmapItem = item.getRoadmapItemId() != null
                            ? roadmapItemMap.get(item.getRoadmapItemId())
                            : null;
                    String roadmapItemTitle = roadmapItem != null ? roadmapItem.getTitle() : null;
                    UUID parentTopicId = (roadmapItem != null && roadmapItem.getParent() != null)
                            ? roadmapItem.getParent().getId()
                            : null;
                    String parentTopicTitle = (roadmapItem != null && roadmapItem.getParent() != null)
                            ? roadmapItem.getParent().getTitle()
                            : null;
                    UUID learningUnitId = roadmapItem != null
                                    && roadmapItem.getItemType() == RoadmapItemType.LEARNING_UNIT
                            ? roadmapItem.getId()
                            : null;
                    return DailyPlanItemResponse.from(
                            item,
                            learningUnitId,
                            roadmapItemTitle,
                            parentTopicId,
                            parentTopicTitle);
                })
                .toList();
    }

    private DailyPlanItemResponse enrichTaskResponse(DailyPlanItem item, UUID userId) {
        if (item == null) {
            return null;
        }
        if (item.getRoadmapItemId() == null) {
            return DailyPlanItemResponse.from(item, null, null, null, null);
        }
        RoadmapItem roadmapItem = roadmapItemRepository
                .findOwnedById(item.getRoadmapItemId(), userId)
                .orElse(null);
        String roadmapItemTitle = roadmapItem != null ? roadmapItem.getTitle() : null;
        UUID parentTopicId = (roadmapItem != null && roadmapItem.getParent() != null)
                ? roadmapItem.getParent().getId()
                : null;
        String parentTopicTitle = (roadmapItem != null && roadmapItem.getParent() != null)
                ? roadmapItem.getParent().getTitle()
                : null;
        UUID learningUnitId = roadmapItem != null
                        && roadmapItem.getItemType() == RoadmapItemType.LEARNING_UNIT
                ? roadmapItem.getId()
                : null;
        return DailyPlanItemResponse.from(
                item,
                learningUnitId,
                roadmapItemTitle,
                parentTopicId,
                parentTopicTitle);
    }

    private DailyPlan requirePlanForUser(UUID planId, UUID userId) {
        return dailyPlanRepository
                .findByIdAndUserId(planId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.DAILY_PLAN_NOT_FOUND,
                        "Daily plan not found: " + planId));
    }

    private DailyPlanItem requireTaskInPlan(
            UUID planId, UUID itemId, UUID userId) {
        requirePlanForUser(planId, userId);
        DailyPlanItem item = dailyPlanItemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.DAILY_PLAN_ITEM_NOT_FOUND,
                        "Task item not found: " + itemId));
        dailyPlanVersionRepository
                .findByIdAndDailyPlanId(item.getDailyPlanVersionId(), planId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.DAILY_PLAN_ITEM_NOT_FOUND,
                        "Task item does not belong to this Daily Plan."));
        return item;
    }

    private void requireNotRemoved(DailyPlanItem item) {
        if (item.isRemoved()) {
            throw new BusinessException(
                    ErrorCode.DAILY_PLAN_ITEM_NOT_FOUND,
                    "Task item not found: " + item.getId());
        }
    }

    private RoadmapItem resolveLearningUnit(DailyPlanItem item, UUID userId) {
        if (item.getRoadmapItemId() == null) {
            return null;
        }
        RoadmapItem learningUnit = roadmapItemRepository
                .findOwnedById(item.getRoadmapItemId(), userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "The Learning Unit linked to this task no longer exists."));
        if (learningUnit.getItemType() != RoadmapItemType.LEARNING_UNIT) {
            throw new BusinessException(
                    ErrorCode.INVALID_PLAN_TRANSITION,
                    "A Roadmap-backed Daily Plan task must reference a Learning Unit.");
        }
        return learningUnit;
    }

    private RoadmapItem resolveCorrectionLearningUnit(
            ProgressEntry original, UUID userId) {
        if (original.getLearningUnitId() == null) {
            return null;
        }
        RoadmapItem learningUnit = roadmapItemRepository
                .findOwnedById(original.getLearningUnitId(), userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "The Learning Unit linked to this progress entry no longer exists."));
        if (learningUnit.getItemType() != RoadmapItemType.LEARNING_UNIT
                || original.getRoadmapVersionId() == null
                || !original.getRoadmapVersionId().equals(
                        learningUnit.getRoadmapVersion().getId())) {
            throw new BusinessException(
                    ErrorCode.INVALID_PLAN_TRANSITION,
                    "The progress entry does not reference a valid Learning Unit version.");
        }
        return learningUnit;
    }

    private void validateRoadmapBackedTasks(
            DailyPlan plan,
            List<DailyPlanItem> items,
            UUID userId) {
        for (DailyPlanItem item : items) {
            RoadmapItem learningUnit = resolveLearningUnit(item, userId);
            if (learningUnit == null) {
                continue;
            }
            Roadmap roadmap = learningUnit.getRoadmapVersion().getRoadmap();
            if (plan.getRoadmapId() == null
                    || !plan.getRoadmapId().equals(roadmap.getId())) {
                throw new BusinessException(
                        ErrorCode.INVALID_PLAN_TRANSITION,
                        "A task's Learning Unit must belong to the Daily Plan Roadmap.");
            }
            if (roadmap.getActiveVersionId() == null
                    || !roadmap.getActiveVersionId().equals(
                            learningUnit.getRoadmapVersion().getId())) {
                throw new BusinessException(
                        ErrorCode.INVALID_PLAN_TRANSITION,
                        "A task's Learning Unit must belong to the ACTIVE RoadmapVersion.");
            }
        }
    }

    private DailyTaskStatus toDailyTaskStatus(
            com.codegym.aiplanning.entity.daily.ProgressEntryStatus status) {
        return switch (status) {
            case COMPLETED -> DailyTaskStatus.COMPLETED;
            case PARTIALLY_COMPLETED -> DailyTaskStatus.PARTIALLY_COMPLETED;
            case SKIPPED -> DailyTaskStatus.SKIPPED;
        };
    }

    private DailyPlan requirePlanForUserForUpdate(UUID planId, UUID userId) {
        return dailyPlanRepository
                .findByIdAndUserIdForUpdate(planId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.DAILY_PLAN_NOT_FOUND,
                        "Daily plan not found: " + planId));
    }

    private DailyPlanVersion requireVersion(UUID planId, UUID versionId) {
        return dailyPlanVersionRepository
                .findByIdAndDailyPlanId(versionId, planId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.DAILY_PLAN_VERSION_NOT_FOUND,
                        "Daily Plan version not found: " + versionId));
    }

    private DailyPlanVersion requireActiveVersion(DailyPlan plan) {
        if (plan.getActiveVersionId() == null) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Daily plan active version is not initialized.");
        }
        DailyPlanVersion version = dailyPlanVersionRepository
                .findByIdAndDailyPlanId(plan.getActiveVersionId(), plan.getId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INTERNAL_ERROR,
                        "Active version not found: " + plan.getActiveVersionId()));
        if (version.getStatus() != DailyPlanVersionStatus.ACTIVE) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Daily plan active-version pointer does not reference an ACTIVE version.");
        }
        return version;
    }

    private DailyPlanVersion requireEditableDraftVersion(
            DailyPlan plan, UUID versionId) {
        if (plan.getStatus() != DailyPlanStatus.DRAFT
                && plan.getStatus() != DailyPlanStatus.READY) {
            throw new BusinessException(
                    ErrorCode.DAILY_PLAN_LOCKED,
                    "Cannot edit a Daily Plan after execution starts.");
        }
        DailyPlanVersion version = requireVersion(plan.getId(), versionId);
        if (!version.isDraft()) {
            throw new BusinessException(
                    ErrorCode.DAILY_PLAN_LOCKED,
                    "Only a DRAFT Daily Plan version can be modified.");
        }
        return version;
    }

    private DailyPlanVersionResponse versionResponse(DailyPlanVersion version) {
        return DailyPlanVersionResponse.from(
                version,
                dailyPlanItemRepository.findByDailyPlanVersionIdOrderByOrderIndexAsc(
                        version.getId()));
    }

    private DailyPlanVersionResponse versionResponse(DailyPlanVersion version, UUID userId) {
        List<DailyPlanItem> items = dailyPlanItemRepository.findByDailyPlanVersionIdOrderByOrderIndexAsc(
                version.getId());
        return DailyPlanVersionResponse.of(version, enrichTaskResponses(items, userId));
    }

    private DailyPlanResponse buildDailyPlanResponse(DailyPlan plan) {
        UUID versionId = plan.getActiveVersionId();
        if (versionId == null) {
            versionId = dailyPlanVersionRepository.findTopByDailyPlanIdOrderByVersionNumberDesc(plan.getId())
                    .map(DailyPlanVersion::getId)
                    .orElse(null);
        }

        if (versionId == null) {
            return DailyPlanResponse.of(plan, null, 60, 0, List.of());
        }

        DailyPlanVersion version = dailyPlanVersionRepository
                .findByIdAndDailyPlanId(versionId, plan.getId())
                .orElse(null);

        List<DailyPlanItem> items = version != null
                ? dailyPlanItemRepository
                        .findByDailyPlanVersionIdOrderByOrderIndexAsc(version.getId())
                : List.of();
        List<DailyPlanItemResponse> itemResponses = enrichTaskResponses(items, plan.getUserId());

        int available = version != null ? version.getAvailableMinutes() : 60;
        int planned = version != null ? version.getTotalPlannedMinutes() : 0;

        return DailyPlanResponse.of(plan, versionId, available, planned, itemResponses);
    }

    private DailyPlanResponse buildDailyPlanResponse(
            DailyPlan plan, DailyPlanVersion version, List<DailyPlanItem> items) {
        if (version == null) {
            return DailyPlanResponse.of(plan, null, 60, 0, List.of());
        }
        return DailyPlanResponse.of(
                plan,
                version.getId(),
                version.getAvailableMinutes(),
                version.getTotalPlannedMinutes(),
                enrichTaskResponses(items, plan.getUserId()));
    }

    private String getUserTimeZone(UUID userId) {
        return userProfileRepository
                .findByUserId(userId)
                .map(UserProfile::getTimeZone)
                .orElse("UTC");
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private UUID extractUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED, "Authentication is invalid.");
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED, "Authentication is invalid.");
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
