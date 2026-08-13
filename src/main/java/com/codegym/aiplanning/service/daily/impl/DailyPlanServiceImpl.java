package com.codegym.aiplanning.service.daily.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.daily.dto.CreateDailyPlanRequest;
import com.codegym.aiplanning.controller.daily.dto.CreateDailyTaskRequest;
import com.codegym.aiplanning.controller.daily.dto.DailyPlanItemResponse;
import com.codegym.aiplanning.controller.daily.dto.DailyPlanResponse;
import com.codegym.aiplanning.controller.daily.dto.RecordPomodoroSessionRequest;
import com.codegym.aiplanning.controller.daily.dto.UpdateTaskStatusRequest;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.daily.DailyPlan;
import com.codegym.aiplanning.entity.daily.DailyPlanItem;
import com.codegym.aiplanning.entity.daily.DailyPlanVersion;
import com.codegym.aiplanning.entity.daily.DailyPlanVersionOrigin;
import com.codegym.aiplanning.entity.daily.DailyTaskStatus;
import com.codegym.aiplanning.entity.daily.ProgressEntry;
import com.codegym.aiplanning.entity.profile.UserProfile;
import com.codegym.aiplanning.repository.daily.DailyPlanItemRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanVersionRepository;
import com.codegym.aiplanning.repository.daily.ProgressEntryRepository;
import com.codegym.aiplanning.repository.profile.UserProfileRepository;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.service.daily.DailyPlanService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
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
    private final AuditLogService auditLogService;

    public DailyPlanServiceImpl(
            DailyPlanRepository dailyPlanRepository,
            DailyPlanVersionRepository dailyPlanVersionRepository,
            DailyPlanItemRepository dailyPlanItemRepository,
            ProgressEntryRepository progressEntryRepository,
            UserProfileRepository userProfileRepository,
            AuditLogService auditLogService) {
        this.dailyPlanRepository = dailyPlanRepository;
        this.dailyPlanVersionRepository = dailyPlanVersionRepository;
        this.dailyPlanItemRepository = dailyPlanItemRepository;
        this.progressEntryRepository = progressEntryRepository;
        this.userProfileRepository = userProfileRepository;
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

        String timeZone = getUserTimeZone(userId);
        int availableMinutes = request.availableMinutes() != null ? request.availableMinutes() : 60;

        DailyPlan plan = DailyPlan.create(userId, request.planDate(), timeZone);
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

        return DailyPlanResponse.of(savedPlan, savedVersion.getId(), savedVersion.getAvailableMinutes(), savedVersion.getTotalPlannedMinutes(), List.of());
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
        return plans.stream().map(this::buildDailyPlanResponse).toList();
    }

    @Override
    @Transactional
    public DailyPlanItemResponse addTaskToPlan(UUID planId, CreateDailyTaskRequest request, Jwt actorJwt) {
        UUID userId = extractUserId(actorJwt);
        String username = extractUsername(actorJwt);

        DailyPlan plan = requirePlanForUser(planId, userId);
        DailyPlanVersion version = requireLatestDraftVersion(plan);

        List<DailyPlanItem> existingItems = dailyPlanItemRepository.findByDailyPlanVersionIdOrderByOrderIndexAsc(version.getId());
        int orderIndex = existingItems.size();
        int plannedMinutes = request.plannedMinutes() != null ? request.plannedMinutes() : 30;

        DailyPlanItem item = DailyPlanItem.create(
                version.getId(),
                request.category(),
                request.title().trim(),
                request.description() != null ? request.description().trim() : null,
                plannedMinutes,
                orderIndex);

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

        DailyPlan plan = requirePlanForUser(planId, userId);
        
        DailyPlanVersion version = dailyPlanVersionRepository.findById(versionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "Version not found: " + versionId));

        if (!version.getDailyPlanId().equals(plan.getId())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Version does not belong to this plan.");
        }

        List<DailyPlanItem> items = dailyPlanItemRepository.findByDailyPlanVersionIdOrderByOrderIndexAsc(version.getId());
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PLAN_TRANSITION, "Cannot activate a version with no tasks.");
        }

        plan.activate(versionId);
        dailyPlanRepository.save(plan);

        auditLogService.logAction(userId, username, AuditEventAction.DAILY_PLAN_UPDATED, "DailyPlan", plan.getId().toString());

        return buildDailyPlanResponse(plan);
    }

    @Override
    @Transactional
    public DailyPlanItemResponse recordProgress(UUID planId, UUID itemId, com.codegym.aiplanning.controller.daily.dto.RecordProgressRequest request, Jwt actorJwt) {
        UUID userId = extractUserId(actorJwt);
        String username = extractUsername(actorJwt);

        DailyPlan plan = requirePlanForUser(planId, userId);
        DailyPlanVersion version = requireActiveVersion(plan);

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

        // Update item status if it's implicitly progressed
        if (request.status() == com.codegym.aiplanning.entity.daily.ProgressEntryStatus.COMPLETED) {
            item.updateStatus(com.codegym.aiplanning.entity.daily.DailyTaskStatus.COMPLETED);
        } else {
            item.updateStatus(com.codegym.aiplanning.entity.daily.DailyTaskStatus.IN_PROGRESS);
        }
        DailyPlanItem savedItem = dailyPlanItemRepository.save(item);

        int actualMinutes = request.actualMinutes() != null ? request.actualMinutes() : item.getPlannedMinutes();
        int percentage = request.status() == com.codegym.aiplanning.entity.daily.ProgressEntryStatus.COMPLETED ? 100 : (request.status() == com.codegym.aiplanning.entity.daily.ProgressEntryStatus.PARTIALLY_COMPLETED ? 50 : 0);

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

        DailyPlan plan = requirePlanForUser(planId, userId);
        DailyPlanVersion version = requireActiveVersion(plan);

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
    public DailyPlanResponse deleteTask(UUID planId, UUID itemId, Jwt actorJwt) {
        UUID userId = extractUserId(actorJwt);
        String username = extractUsername(actorJwt);

        DailyPlan plan = requirePlanForUser(planId, userId);
        DailyPlanVersion version = requireLatestDraftVersion(plan);

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

        return buildDailyPlanResponse(plan);
    }

    private DailyPlan requirePlanForUser(UUID planId, UUID userId) {
        return dailyPlanRepository
                .findByIdAndUserId(planId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.DAILY_PLAN_NOT_FOUND,
                        "Daily plan not found: " + planId));
    }

    private DailyPlanVersion requireActiveVersion(DailyPlan plan) {
        if (plan.getActiveVersionId() == null) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Daily plan active version is not initialized.");
        }
        return dailyPlanVersionRepository
                .findById(plan.getActiveVersionId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INTERNAL_ERROR,
                        "Active version not found: " + plan.getActiveVersionId()));
    }

    private DailyPlanVersion requireLatestDraftVersion(DailyPlan plan) {
        if (plan.getStatus() != com.codegym.aiplanning.entity.daily.DailyPlanStatus.DRAFT) {
            throw new BusinessException(ErrorCode.DAILY_PLAN_LOCKED, "Cannot modify a plan that is not in DRAFT status.");
        }
        return dailyPlanVersionRepository.findTopByDailyPlanIdOrderByVersionNumberDesc(plan.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "No draft version found for plan."));
    }

    private DailyPlanResponse buildDailyPlanResponse(DailyPlan plan) {
        if (plan.getActiveVersionId() == null) {
            UUID draftId = dailyPlanVersionRepository.findTopByDailyPlanIdOrderByVersionNumberDesc(plan.getId())
                    .map(DailyPlanVersion::getId)
                    .orElse(null);
            return DailyPlanResponse.of(plan, draftId, 60, 0, List.of());
        }
        DailyPlanVersion version = dailyPlanVersionRepository
                .findById(plan.getActiveVersionId())
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

        return DailyPlanResponse.of(plan, plan.getActiveVersionId(), available, planned, items);
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
