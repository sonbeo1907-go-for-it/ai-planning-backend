package com.codegym.aiplanning.service.daily.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.daily.dto.CreateDailyPlanRequest;
import com.codegym.aiplanning.controller.daily.dto.CreateDailyTaskRequest;
import com.codegym.aiplanning.controller.daily.dto.DailyPlanItemResponse;
import com.codegym.aiplanning.controller.daily.dto.DailyPlanResponse;
import com.codegym.aiplanning.controller.daily.dto.DailyPlanVersionResponse;
import com.codegym.aiplanning.controller.daily.dto.RecordPomodoroSessionRequest;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.daily.DailyPlan;
import com.codegym.aiplanning.entity.daily.DailyPlanItem;
import com.codegym.aiplanning.entity.daily.DailyPlanStatus;
import com.codegym.aiplanning.entity.daily.DailyPlanVersion;
import com.codegym.aiplanning.entity.daily.DailyPlanVersionOrigin;
import com.codegym.aiplanning.entity.daily.DailyPlanVersionStatus;
import com.codegym.aiplanning.entity.daily.DailyTaskStatus;
import com.codegym.aiplanning.entity.daily.ProgressEntry;
import com.codegym.aiplanning.entity.profile.UserProfile;
import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapItem;
import com.codegym.aiplanning.entity.roadmap.RoadmapStatus;
import com.codegym.aiplanning.repository.daily.DailyPlanItemRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanVersionRepository;
import com.codegym.aiplanning.repository.daily.ProgressEntryRepository;
import com.codegym.aiplanning.repository.profile.UserProfileRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapItemRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapRepository;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.service.daily.DailyPlanService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    private final AuditLogService auditLogService;

    public DailyPlanServiceImpl(
            DailyPlanRepository dailyPlanRepository,
            DailyPlanVersionRepository dailyPlanVersionRepository,
            DailyPlanItemRepository dailyPlanItemRepository,
            ProgressEntryRepository progressEntryRepository,
            UserProfileRepository userProfileRepository,
            RoadmapRepository roadmapRepository,
            RoadmapItemRepository roadmapItemRepository,
            AuditLogService auditLogService) {
        this.dailyPlanRepository = dailyPlanRepository;
        this.dailyPlanVersionRepository = dailyPlanVersionRepository;
        this.dailyPlanItemRepository = dailyPlanItemRepository;
        this.progressEntryRepository = progressEntryRepository;
        this.userProfileRepository = userProfileRepository;
        this.roadmapRepository = roadmapRepository;
        this.roadmapItemRepository = roadmapItemRepository;
        this.auditLogService = auditLogService;
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
    public List<DailyPlanResponse> getUserDailyPlans(Jwt actorJwt) {
        UUID userId = extractUserId(actorJwt);
        List<DailyPlan> plans = dailyPlanRepository.findByUserIdOrderByPlanDateDesc(userId);
        if (plans.isEmpty()) {
            return List.of();
        }

        List<DailyPlanVersion> versions = dailyPlanVersionRepository
                .findCurrentVersionsByDailyPlanIds(
                        plans.stream().map(DailyPlan::getId).toList());
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

        return plans.stream()
                .map(plan -> {
                    DailyPlanVersion version = versionByPlanId.get(plan.getId());
                    List<DailyPlanItem> items = version == null
                            ? List.of()
                            : itemsByVersionId.getOrDefault(version.getId(), List.of());
                    return buildDailyPlanResponse(plan, version, items);
                })
                .toList();
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

        Map<UUID, List<DailyPlanItem>> itemsByVersionId = new HashMap<>();
        for (DailyPlanItem item : dailyPlanItemRepository.findByDailyPlanVersionIds(
                versions.stream().map(DailyPlanVersion::getId).toList())) {
            itemsByVersionId
                    .computeIfAbsent(
                            item.getDailyPlanVersionId(), ignored -> new ArrayList<>())
                    .add(item);
        }
        return versions.stream()
                .map(version -> DailyPlanVersionResponse.from(
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
        return versionResponse(version);
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
        return DailyPlanVersionResponse.from(draft, savedCopies);
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

        UUID roadmapItemId = request.roadmapItemId();
        if (roadmapItemId != null) {
            RoadmapItem roadmapItem = roadmapItemRepository.findById(roadmapItemId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Roadmap item not found"));
            Roadmap roadmap = roadmapItem.getRoadmapVersion().getRoadmap();
            if (!roadmap.getOwner().getId().equals(userId)) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Roadmap item not found");
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

        return DailyPlanItemResponse.from(savedItem);
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
    public DailyPlanItemResponse recordProgress(UUID planId, UUID itemId, com.codegym.aiplanning.controller.daily.dto.RecordProgressRequest request, Jwt actorJwt) {
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

        DailyTaskStatus taskStatus = switch (request.status()) {
            case COMPLETED -> DailyTaskStatus.COMPLETED;
            case PARTIALLY_COMPLETED -> DailyTaskStatus.PARTIALLY_COMPLETED;
            case SKIPPED -> DailyTaskStatus.SKIPPED;
        };
        item.updateStatus(taskStatus);
        DailyPlanItem savedItem = dailyPlanItemRepository.save(item);

        int actualMinutes = request.actualMinutes() != null ? request.actualMinutes() : item.getPlannedMinutes();
        int percentage = taskStatus.completionPercentage();

        ProgressEntry entry = ProgressEntry.create(
                userId, 
                itemId, 
                request.status(), 
                actualMinutes, 
                percentage,
                request.actualResult(),
                request.difficulty(),
                request.understandingRating(),
                request.note(),
                null // supersedes logic can be added later or mapped here if provided
        );
        progressEntryRepository.save(entry);

        auditLogService.logAction(
                userId,
                username,
                AuditEventAction.PROGRESS_RECORDED,
                "DailyPlanItem",
                savedItem.getId().toString());

        return DailyPlanItemResponse.from(savedItem);
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

        if (item.getStatus() == DailyTaskStatus.NOT_STARTED) {
            item.updateStatus(DailyTaskStatus.IN_PROGRESS);
            dailyPlanItemRepository.save(item);
        }

        int pomodoroMinutes = request.completedMinutes() != null ? request.completedMinutes() : 25;

        com.codegym.aiplanning.entity.daily.ProgressEntryStatus progressStatus = 
                item.getStatus() == DailyTaskStatus.COMPLETED 
                        ? com.codegym.aiplanning.entity.daily.ProgressEntryStatus.COMPLETED 
                        : com.codegym.aiplanning.entity.daily.ProgressEntryStatus.PARTIALLY_COMPLETED;

        ProgressEntry entry = ProgressEntry.create(
                userId,
                itemId,
                progressStatus,
                pomodoroMinutes,
                item.getStatus() == DailyTaskStatus.COMPLETED ? 100 : 50,
                "Pomodoro session",
                null,
                null,
                null,
                null);
        progressEntryRepository.save(entry);

        auditLogService.logAction(
                userId,
                username,
                AuditEventAction.PROGRESS_RECORDED,
                "DailyPlanItem",
                item.getId().toString());

        return DailyPlanItemResponse.from(item);
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

        if (progressEntryRepository.existsByDailyPlanItemId(itemId)) {
            throw new BusinessException(
                    ErrorCode.DAILY_PLAN_ITEM_HAS_PROGRESS,
                    "A task with progress history cannot be deleted.");
        }

        dailyPlanItemRepository.delete(item);

        int newTotalMinutes = Math.max(0, version.getTotalPlannedMinutes() - item.getPlannedMinutes());
        version.updateTotalPlannedMinutes(newTotalMinutes);
        dailyPlanVersionRepository.save(version);

        auditLogService.logAction(
                userId,
                username,
                AuditEventAction.DAILY_PLAN_UPDATED,
                "DailyPlanItem",
                itemId.toString());

        List<DailyPlanItem> remainingItems = dailyPlanItemRepository
                .findByDailyPlanVersionIdOrderByOrderIndexAsc(versionId);
        return DailyPlanVersionResponse.from(version, remainingItems);
    }

    private DailyPlan requirePlanForUser(UUID planId, UUID userId) {
        return dailyPlanRepository
                .findByIdAndUserId(planId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.DAILY_PLAN_NOT_FOUND,
                        "Daily plan not found: " + planId));
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

        List<DailyPlanItemResponse> items = version != null
                ? dailyPlanItemRepository
                        .findByDailyPlanVersionIdOrderByOrderIndexAsc(version.getId())
                        .stream()
                        .map(DailyPlanItemResponse::from)
                        .toList()
                : List.of();

        int available = version != null ? version.getAvailableMinutes() : 60;
        int planned = version != null ? version.getTotalPlannedMinutes() : 0;

        return DailyPlanResponse.of(plan, versionId, available, planned, items);
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
                items.stream().map(DailyPlanItemResponse::from).toList());
    }

    private String getUserTimeZone(UUID userId) {
        return userProfileRepository
                .findByUserId(userId)
                .map(UserProfile::getTimeZone)
                .orElse("UTC");
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
