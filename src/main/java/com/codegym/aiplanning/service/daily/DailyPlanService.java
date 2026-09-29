package com.codegym.aiplanning.service.daily;

import com.codegym.aiplanning.controller.daily.dto.CreateDailyPlanRequest;
import com.codegym.aiplanning.controller.daily.dto.CreateDailyTaskRequest;
import com.codegym.aiplanning.controller.daily.dto.DailyPlanItemResponse;
import com.codegym.aiplanning.controller.daily.dto.DailyPlanResponse;
import com.codegym.aiplanning.controller.daily.dto.DailyPlanVersionResponse;
import com.codegym.aiplanning.controller.daily.dto.RecordPomodoroSessionRequest;
import com.codegym.aiplanning.controller.daily.dto.ProgressEntryResponse;
import com.codegym.aiplanning.controller.daily.dto.DailyPlanTaskProgressHistoryResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import com.codegym.aiplanning.controller.daily.dto.DailyPlanSummaryResponse;
import com.codegym.aiplanning.controller.daily.dto.UpdateDailyTaskRequest;
import com.codegym.aiplanning.controller.daily.dto.UpdateDailyPlanBudgetRequest;
import com.codegym.aiplanning.entity.daily.DailyPlanStatus;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.codegym.aiplanning.entity.ai.AiProviderConfig;

import com.codegym.aiplanning.controller.daily.dto.AvailableLearningUnitResponse;

public interface DailyPlanService {

    List<AvailableLearningUnitResponse> getAvailableLearningUnits(UUID planId, Jwt actorJwt);

    DailyPlanResponse createDailyPlan(CreateDailyPlanRequest request, Jwt actorJwt);

    DailyPlanResponse getTodayPlan(Jwt actorJwt);

    DailyPlanResponse getPlanById(UUID planId, Jwt actorJwt);

    Page<DailyPlanSummaryResponse> getUserDailyPlans(
            DailyPlanStatus status,
            UUID roadmapId,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable,
            Jwt actorJwt);

    List<DailyPlanVersionResponse> getVersions(UUID planId, Jwt actorJwt);

    DailyPlanVersionResponse getVersion(UUID planId, UUID versionId, Jwt actorJwt);

    DailyPlanVersionResponse createDraftVersion(UUID planId, Jwt actorJwt);

    DailyPlanVersionResponse updateDraftBudget(
            UUID planId,
            UUID versionId,
            UpdateDailyPlanBudgetRequest request,
            Jwt actorJwt);

    DailyPlanVersionResponse generateAiDraftVersion(
            UUID planId, String idempotencyKey, Jwt actorJwt);

    DailyPlanVersionResponse generateAiDraftVersionWithProviderConfig(
            UUID planId,
            UUID ownerId,
            String ownerEmail,
            String generationRequestKey,
            AiProviderConfig providerConfig);

    DailyPlanVersionResponse generateAiDraftVersionWithProviderConfig(
            UUID planId,
            UUID ownerId,
            String ownerEmail,
            String generationRequestKey,
            AiProviderConfig providerConfig,
            String systemPrompt);


    DailyPlanItemResponse addTaskToPlan(
            UUID planId, UUID versionId, CreateDailyTaskRequest request, Jwt actorJwt);

    DailyPlanVersionResponse updateTask(
            UUID planId,
            UUID versionId,
            UUID itemId,
            UpdateDailyTaskRequest request,
            Jwt actorJwt);

    DailyPlanItemResponse updateTaskStatus(
            UUID planId,
            UUID itemId,
            com.codegym.aiplanning.controller.daily.dto.UpdateTaskStatusRequest request,
            Jwt actorJwt);

    DailyPlanResponse activateVersion(UUID planId, UUID versionId, Jwt actorJwt);

    DailyPlanItemResponse recordProgress(
            UUID planId,
            UUID itemId,
            com.codegym.aiplanning.controller.daily.dto.RecordProgressRequest request,
            String idempotencyKey,
            Jwt actorJwt);

    default DailyPlanItemResponse recordProgress(
            UUID planId,
            UUID itemId,
            com.codegym.aiplanning.controller.daily.dto.RecordProgressRequest request,
            Jwt actorJwt) {
        return recordProgress(planId, itemId, request, null, actorJwt);
    }

    java.util.List<ProgressEntryResponse> getProgressHistory(
            UUID planId, UUID itemId, Jwt actorJwt);

    List<DailyPlanTaskProgressHistoryResponse> getPlanProgressHistory(
            UUID planId, Jwt actorJwt);

    ProgressEntryResponse correctProgress(
            UUID planId,
            UUID itemId,
            UUID progressEntryId,
            com.codegym.aiplanning.controller.daily.dto.RecordProgressRequest request,
            String idempotencyKey,
            Jwt actorJwt);

    DailyPlanItemResponse recordPomodoroSession(UUID planId, UUID itemId, RecordPomodoroSessionRequest request, Jwt actorJwt);

    DailyPlanVersionResponse deleteTask(
            UUID planId, UUID versionId, UUID itemId, Jwt actorJwt);
}
