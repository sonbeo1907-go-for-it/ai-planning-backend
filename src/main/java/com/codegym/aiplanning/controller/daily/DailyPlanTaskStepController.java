package com.codegym.aiplanning.controller.daily;

import com.codegym.aiplanning.common.api.ApiResponse;
import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.controller.daily.dto.CreateTaskStepRequest;
import com.codegym.aiplanning.controller.daily.dto.DailyPlanTaskStepsResponse;
import com.codegym.aiplanning.controller.daily.dto.UpdateTaskStepCompletionRequest;
import com.codegym.aiplanning.controller.daily.dto.UpdateTaskStepRequest;
import com.codegym.aiplanning.service.daily.step.DailyPlanTaskStepService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstant.DAILY_PLANS)
@Tag(name = "Daily Plan Task Steps", description = "Version-safe actionable checklist steps")
@PreAuthorize("hasRole('USER')")
public class DailyPlanTaskStepController {

    private final DailyPlanTaskStepService taskStepService;

    public DailyPlanTaskStepController(DailyPlanTaskStepService taskStepService) {
        this.taskStepService = taskStepService;
    }

    @GetMapping(ApiConstant.DAILY_PLAN_ITEM_STEPS)
    @Operation(summary = "List ordered steps for one exact Daily Plan task")
    public ApiResponse<DailyPlanTaskStepsResponse> getSteps(
            @PathVariable UUID planId,
            @PathVariable UUID versionId,
            @PathVariable UUID itemId,
            @AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(taskStepService.getSteps(
                planId,
                versionId,
                itemId,
                actorJwt));
    }

    @PostMapping(ApiConstant.DAILY_PLAN_ITEM_STEPS)
    @Operation(summary = "Add a manual step to one DRAFT Daily Plan task")
    public ApiResponse<DailyPlanTaskStepsResponse> createStep(
            @PathVariable UUID planId,
            @PathVariable UUID versionId,
            @PathVariable UUID itemId,
            @Valid @RequestBody CreateTaskStepRequest request,
            @AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(taskStepService.createStep(
                planId,
                versionId,
                itemId,
                request,
                actorJwt));
    }

    @PatchMapping(ApiConstant.DAILY_PLAN_ITEM_STEP_BY_ID)
    @Operation(summary = "Edit or reorder one step in a DRAFT Daily Plan task")
    public ApiResponse<DailyPlanTaskStepsResponse> updateStep(
            @PathVariable UUID planId,
            @PathVariable UUID versionId,
            @PathVariable UUID itemId,
            @PathVariable UUID stepId,
            @Valid @RequestBody UpdateTaskStepRequest request,
            @AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(taskStepService.updateStep(
                planId,
                versionId,
                itemId,
                stepId,
                request,
                actorJwt));
    }

    @DeleteMapping(ApiConstant.DAILY_PLAN_ITEM_STEP_BY_ID)
    @Operation(summary = "Remove one step from a DRAFT Daily Plan task")
    public ApiResponse<DailyPlanTaskStepsResponse> deleteStep(
            @PathVariable UUID planId,
            @PathVariable UUID versionId,
            @PathVariable UUID itemId,
            @PathVariable UUID stepId,
            @RequestParam(name = "entityVersion") long expectedEntityVersion,
            @AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(taskStepService.deleteStep(
                planId,
                versionId,
                itemId,
                stepId,
                expectedEntityVersion,
                actorJwt));
    }

    @PutMapping(ApiConstant.DAILY_PLAN_ITEM_STEP_COMPLETION)
    @Operation(
            summary = "Idempotently set Task Step completion",
            description = "Updates runtime checklist state without recording parent-task or Roadmap progress.")
    public ApiResponse<DailyPlanTaskStepsResponse> setCompletion(
            @PathVariable UUID planId,
            @PathVariable UUID versionId,
            @PathVariable UUID itemId,
            @PathVariable UUID stepId,
            @Valid @RequestBody UpdateTaskStepCompletionRequest request,
            @AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(taskStepService.setCompletion(
                planId,
                versionId,
                itemId,
                stepId,
                request,
                actorJwt));
    }
}
