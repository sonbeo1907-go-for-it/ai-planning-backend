package com.codegym.aiplanning.controller.daily;

import com.codegym.aiplanning.common.api.ApiError;
import com.codegym.aiplanning.common.api.ApiResponse;
import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.controller.daily.dto.CreateDailyPlanRequest;
import com.codegym.aiplanning.controller.daily.dto.CreateDailyTaskRequest;
import com.codegym.aiplanning.controller.daily.dto.DailyPlanItemResponse;
import com.codegym.aiplanning.controller.daily.dto.DailyPlanResponse;
import com.codegym.aiplanning.controller.daily.dto.RecordPomodoroSessionRequest;
import com.codegym.aiplanning.controller.daily.dto.UpdateTaskStatusRequest;
import com.codegym.aiplanning.service.daily.DailyPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstant.DAILY_PLANS)
@Tag(name = "Daily Plans", description = "Daily Learning Plan & Interactive Task Checklist API (US-TSK-01-MANUAL)")
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
public class DailyPlanController {

    private final DailyPlanService dailyPlanService;

    public DailyPlanController(DailyPlanService dailyPlanService) {
        this.dailyPlanService = dailyPlanService;
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

    @GetMapping("/today")
    @Operation(
            summary = "Get today's daily plan (US-TSK-01-MANUAL)",
            description = "Returns today's daily plan and task checklist based on the user's timezone.")
    public ApiResponse<DailyPlanResponse> getTodayPlan(@AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(dailyPlanService.getTodayPlan(actorJwt));
    }

    @GetMapping("/{planId}")
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
    public ApiResponse<List<DailyPlanResponse>> getUserDailyPlans(@AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(dailyPlanService.getUserDailyPlans(actorJwt));
    }

    @PostMapping("/{planId}/items")
    @Operation(
            summary = "Add a manual task to daily plan (US-TSK-01-MANUAL)",
            description = "Adds a manual learning task item to the active version of the daily plan.")
    public ApiResponse<DailyPlanItemResponse> addTaskToPlan(
            @PathVariable UUID planId,
            @Valid @RequestBody CreateDailyTaskRequest request,
            @AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(dailyPlanService.addTaskToPlan(planId, request, actorJwt));
    }

    @PatchMapping("/{planId}/items/{itemId}/status")
    @Operation(
            summary = "Check or update task completion status (US-TSK-01-MANUAL)",
            description = "Interactive checklist: Updates task status (COMPLETED, IN_PROGRESS, etc.), records progress entry, and recalculates daily percentage.")
    public ApiResponse<DailyPlanItemResponse> updateTaskStatus(
            @PathVariable UUID planId,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateTaskStatusRequest request,
            @AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(dailyPlanService.updateTaskStatus(planId, itemId, request, actorJwt));
    }

    @PostMapping("/{planId}/items/{itemId}/pomodoro")
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

    @DeleteMapping("/{planId}/items/{itemId}")
    @Operation(
            summary = "Delete manual task from daily plan (US-TSK-01-MANUAL)",
            description = "Deletes a manual task and returns the updated daily plan with recalculated percentage.")
    public ApiResponse<DailyPlanResponse> deleteTask(
            @PathVariable UUID planId,
            @PathVariable UUID itemId,
            @AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(dailyPlanService.deleteTask(planId, itemId, actorJwt));
    }
}
