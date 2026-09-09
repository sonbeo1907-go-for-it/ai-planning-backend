package com.codegym.aiplanning.controller.daily;

import com.codegym.aiplanning.common.api.ApiError;
import com.codegym.aiplanning.common.api.ApiResponse;
import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.controller.ai.dto.AiExecutionResponse;
import com.codegym.aiplanning.controller.daily.dto.AvailableLearningUnitResponse;
import com.codegym.aiplanning.controller.daily.dto.CreateDailyPlanRequest;
import com.codegym.aiplanning.controller.daily.dto.CreateDailyTaskRequest;
import com.codegym.aiplanning.controller.daily.dto.DailyPlanItemResponse;
import com.codegym.aiplanning.controller.daily.dto.DailyPlanResponse;
import com.codegym.aiplanning.controller.daily.dto.DailyPlanSummaryResponse;
import com.codegym.aiplanning.controller.daily.dto.DailyPlanVersionResponse;
import com.codegym.aiplanning.controller.daily.dto.RecordProgressRequest;
import com.codegym.aiplanning.controller.daily.dto.RecordPomodoroSessionRequest;
import com.codegym.aiplanning.controller.daily.dto.UpdateDailyTaskRequest;
import com.codegym.aiplanning.entity.daily.DailyPlanStatus;
import com.codegym.aiplanning.service.daily.DailyPlanService;
import com.codegym.aiplanning.service.ai.execution.AiExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.time.LocalDate;
import java.net.URI;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping(ApiConstant.DAILY_PLANS)
@Tag(name = "Daily Plans", description = "Daily Learning Plan & Interactive Task Checklist API (US-TSK-01-MANUAL)")
@PreAuthorize("hasRole('USER')")
public class DailyPlanController {

    private final DailyPlanService dailyPlanService;
    private final AiExecutionService aiExecutionService;

    public DailyPlanController(
            DailyPlanService dailyPlanService,
            AiExecutionService aiExecutionService) {
        this.dailyPlanService = dailyPlanService;
        this.aiExecutionService = aiExecutionService;
    }

    @PostMapping
    @Operation(
            summary = "Start new day plan (US-TSK-01-MANUAL)",
            description = "Creates a new daily plan for the specified date and initial committed minutes.",
            responses = {
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
                        responseCode = "200", description = "Daily plan created successfully"),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
                        responseCode = "409",
                        description = "Daily plan already exists for this date",
                        content = @Content(schema = @Schema(implementation = ApiError.class)))
            })
    public ApiResponse<DailyPlanResponse> createDailyPlan(
            @Valid @RequestBody CreateDailyPlanRequest request,
            @AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(dailyPlanService.createDailyPlan(request, actorJwt));
    }

    @GetMapping(ApiConstant.DAILY_PLAN_TODAY)
    @Operation(
            summary = "Get today's daily plan (US-TSK-01-MANUAL)",
            description = "Returns today's daily plan and task checklist based on the user's timezone.")
    public ApiResponse<DailyPlanResponse> getTodayPlan(@AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(dailyPlanService.getTodayPlan(actorJwt));
    }

    @GetMapping(ApiConstant.DAILY_PLAN_BY_ID)
    @Operation(
            summary = "Get daily plan details by ID (US-TSK-01-MANUAL)",
            description = "Returns a specific daily plan and its items. Only accessible by the plan owner.")
    public ApiResponse<DailyPlanResponse> getPlanById(
            @PathVariable UUID planId,
            @AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(dailyPlanService.getPlanById(planId, actorJwt));
    }

    @GetMapping
    @Operation(
            summary = "List all daily plans for user (US-TSK-01-MANUAL)",
            description = "Returns all historical daily plans belonging to the authenticated user.")
    public ApiResponse<Page<DailyPlanSummaryResponse>> getUserDailyPlans(
            @RequestParam(required = false) DailyPlanStatus status,
            @RequestParam(required = false) UUID roadmapId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @ParameterObject
                    @PageableDefault(
                            size = 20,
                            sort = "planDate",
                            direction = org.springframework.data.domain.Sort.Direction.DESC)
                    Pageable pageable,
            @AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(dailyPlanService.getUserDailyPlans(
                status, roadmapId, from, to, pageable, actorJwt));
    }

    @GetMapping(ApiConstant.DAILY_PLAN_VERSIONS)
    @Operation(summary = "List all versions belonging to one owner-scoped Daily Plan")
    public ApiResponse<List<DailyPlanVersionResponse>> getVersions(
            @PathVariable UUID planId, @AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(dailyPlanService.getVersions(planId, actorJwt));
    }

    @GetMapping(ApiConstant.DAILY_PLAN_VERSION_BY_ID)
    @Operation(summary = "Read one exact Daily Plan version and its tasks")
    public ApiResponse<DailyPlanVersionResponse> getVersion(
            @PathVariable UUID planId,
            @PathVariable UUID versionId,
            @AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(dailyPlanService.getVersion(planId, versionId, actorJwt));
    }

    @PostMapping(ApiConstant.DAILY_PLAN_VERSIONS)
    @Operation(summary = "Create the next editable Daily Plan draft from the active version")
    public ApiResponse<DailyPlanVersionResponse> createDraftVersion(
            @PathVariable UUID planId, @AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(dailyPlanService.createDraftVersion(planId, actorJwt));
    }

    @PostMapping(ApiConstant.DAILY_PLAN_GENERATE_AI)
    @Operation(
            summary = "Queue AI Daily Plan generation",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "202",
                    description = "AI execution accepted for asynchronous processing"))
    public ResponseEntity<ApiResponse<AiExecutionResponse>> generateAiDraftVersion(
            @PathVariable UUID planId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal Jwt actorJwt) {
        AiExecutionResponse execution = aiExecutionService.submitDailyPlanGeneration(
                userId(actorJwt), planId, idempotencyKey);
        return accepted(execution);
    }

    @PostMapping(ApiConstant.DAILY_PLAN_REGENERATE_AI)
    @Operation(
            summary = "Queue AI Daily Plan regeneration as a new DRAFT version",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "202",
                    description = "AI execution accepted for asynchronous processing"))
    public ResponseEntity<ApiResponse<AiExecutionResponse>> regenerateAiDraftVersion(
            @PathVariable UUID planId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal Jwt actorJwt) {
        AiExecutionResponse execution = aiExecutionService.submitDailyPlanRegeneration(
                userId(actorJwt), planId, idempotencyKey);
        return accepted(execution);
    }

    @GetMapping(ApiConstant.DAILY_PLAN_CURRENT_AI_EXECUTION)
    @Operation(summary = "Get the latest Daily Plan AI execution")
    public ApiResponse<AiExecutionResponse> getLatestAiExecution(
            @PathVariable UUID planId,
            @AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(aiExecutionService.getLatestDailyPlanExecution(
                userId(actorJwt), planId));
    }

    @GetMapping(ApiConstant.DAILY_PLAN_AVAILABLE_LEARNING_UNITS)
    @Operation(
            summary = "Get available learning units from the active roadmap for daily plan task linkage (RMP-PROG-02)",
            description = "Returns executable learning units along with their parent topic and milestone from the active roadmap linked to this plan.")
    public ApiResponse<List<AvailableLearningUnitResponse>> getAvailableLearningUnits(
            @PathVariable UUID planId,
            @AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(dailyPlanService.getAvailableLearningUnits(planId, actorJwt));
    }

    @PostMapping(ApiConstant.DAILY_PLAN_VERSION_ITEMS)
    @Operation(
            summary = "Add a manual task to daily plan (US-TSK-01-MANUAL)",
            description = "Adds a manual learning task to one exact DRAFT version.")
    public ApiResponse<DailyPlanItemResponse> addTaskToPlan(
            @PathVariable UUID planId,
            @PathVariable UUID versionId,
            @Valid @RequestBody CreateDailyTaskRequest request,
            @AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(
                dailyPlanService.addTaskToPlan(planId, versionId, request, actorJwt));
    }

    @PatchMapping(ApiConstant.DAILY_PLAN_VERSION_ITEM_BY_ID)
    @Operation(summary = "Edit and reorder a task in one exact DRAFT Daily Plan version")
    public ApiResponse<DailyPlanVersionResponse> updateTask(
            @PathVariable UUID planId,
            @PathVariable UUID versionId,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateDailyTaskRequest request,
            @AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(dailyPlanService.updateTask(
                planId, versionId, itemId, request, actorJwt));
    }

    @PostMapping(ApiConstant.DAILY_PLAN_VERSION_ACTIVATE)
    @Operation(
            summary = "Activate a daily plan version (US-PLN-01-MANUAL)",
            description = "Activates one exact DRAFT version and preserves the previous ACTIVE version as SUPERSEDED.")
    public ApiResponse<DailyPlanResponse> activateVersion(
            @PathVariable UUID planId,
            @PathVariable UUID versionId,
            @AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(dailyPlanService.activateVersion(planId, versionId, actorJwt));
    }

    @PostMapping(ApiConstant.DAILY_PLAN_ITEM_PROGRESS)
    @Operation(
            summary = "Record detailed task progress (US-PLN-01-MANUAL)",
            description = "Appends a progress entry for a task with detailed tracking including difficulty and notes.")
    public ApiResponse<DailyPlanItemResponse> recordProgress(
            @PathVariable UUID planId,
            @PathVariable UUID itemId,
            @Valid @RequestBody RecordProgressRequest request,
            @AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(dailyPlanService.recordProgress(planId, itemId, request, actorJwt));
    }

    @PostMapping(ApiConstant.DAILY_PLAN_ITEM_POMODORO)
    @Operation(
            summary = "Record a completed Pomodoro focus session (US-TSK-02)",
            description = "Records a completed Pomodoro study session (default 25 minutes) for a specific task and updates progress entries.")
    public ApiResponse<DailyPlanItemResponse> recordPomodoroSession(
            @PathVariable UUID planId,
            @PathVariable UUID itemId,
            @Valid @RequestBody RecordPomodoroSessionRequest request,
            @AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(dailyPlanService.recordPomodoroSession(planId, itemId, request, actorJwt));
    }

    @DeleteMapping(ApiConstant.DAILY_PLAN_VERSION_ITEM_BY_ID)
    @Operation(
            summary = "Delete manual task from daily plan (US-TSK-01-MANUAL)",
            description = "Deletes a manual task and returns the updated daily plan with recalculated percentage.")
    public ApiResponse<DailyPlanVersionResponse> deleteTask(
            @PathVariable UUID planId,
            @PathVariable UUID versionId,
            @PathVariable UUID itemId,
            @AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(
                dailyPlanService.deleteTask(planId, versionId, itemId, actorJwt));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private ResponseEntity<ApiResponse<AiExecutionResponse>> accepted(
            AiExecutionResponse execution) {
        URI statusLocation = URI.create(
                ApiConstant.AI_EXECUTIONS + "/" + execution.id());
        return ResponseEntity.accepted()
                .location(statusLocation)
                .body(ApiResponse.of(execution));
    }
}
