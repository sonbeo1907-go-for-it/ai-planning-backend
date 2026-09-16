package com.codegym.aiplanning.controller.guidance;

import com.codegym.aiplanning.common.api.ApiResponse;
import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.controller.ai.dto.AiExecutionResponse;
import com.codegym.aiplanning.controller.guidance.dto.RegenerateTaskGuidanceRequest;
import com.codegym.aiplanning.controller.guidance.dto.TaskGuidanceOverviewResponse;
import com.codegym.aiplanning.controller.guidance.dto.TaskGuidanceRevisionResponse;
import com.codegym.aiplanning.service.ai.execution.AiExecutionService;
import com.codegym.aiplanning.service.guidance.TaskGuidanceQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstant.DAILY_PLANS)
@PreAuthorize("hasRole('USER')")
@Tag(
        name = "AI Task Guidance",
        description = "Owner-scoped advisory guidance for existing Daily Plan Task Steps")
public class TaskGuidanceController {

    private final TaskGuidanceQueryService queryService;
    private final AiExecutionService aiExecutionService;

    public TaskGuidanceController(
            TaskGuidanceQueryService queryService,
            AiExecutionService aiExecutionService) {
        this.queryService = queryService;
        this.aiExecutionService = aiExecutionService;
    }

    @GetMapping(ApiConstant.DAILY_PLAN_ITEM_GUIDANCE)
    @Operation(summary = "Get latest Task Guidance and revision history")
    public ApiResponse<TaskGuidanceOverviewResponse> getOverview(
            @PathVariable UUID planId,
            @PathVariable UUID versionId,
            @PathVariable UUID itemId,
            @PageableDefault(size = 10) Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(queryService.getOverview(
                ownerId(jwt),
                planId,
                versionId,
                itemId,
                pageable));
    }

    @GetMapping(ApiConstant.DAILY_PLAN_ITEM_GUIDANCE_BY_REVISION)
    @Operation(summary = "Get one exact Task Guidance revision")
    public ApiResponse<TaskGuidanceRevisionResponse> getRevision(
            @PathVariable UUID planId,
            @PathVariable UUID versionId,
            @PathVariable UUID itemId,
            @PathVariable UUID revisionId,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(queryService.getRevision(
                ownerId(jwt),
                planId,
                versionId,
                itemId,
                revisionId));
    }

    @PostMapping(ApiConstant.DAILY_PLAN_ITEM_GUIDANCE_GENERATE)
    @Operation(
            summary = "Queue initial AI Task Guidance generation",
            description = "Returns HTTP 202. Existing Task Steps remain unchanged and are the "
                    + "only executable checklist.")
    public ResponseEntity<ApiResponse<AiExecutionResponse>> generate(
            @PathVariable UUID planId,
            @PathVariable UUID versionId,
            @PathVariable UUID itemId,
            @RequestHeader(value = "Idempotency-Key", required = false)
                    String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt) {
        return accepted(aiExecutionService.submitTaskGuidanceGeneration(
                ownerId(jwt),
                planId,
                versionId,
                itemId,
                idempotencyKey));
    }

    @PostMapping(ApiConstant.DAILY_PLAN_ITEM_GUIDANCE_REGENERATE)
    @Operation(
            summary = "Queue Task Guidance regeneration",
            description = "Creates a new immutable DRAFT revision and preserves prior guidance.")
    public ResponseEntity<ApiResponse<AiExecutionResponse>> regenerate(
            @PathVariable UUID planId,
            @PathVariable UUID versionId,
            @PathVariable UUID itemId,
            @Valid @RequestBody(required = false)
                    RegenerateTaskGuidanceRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false)
                    String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt) {
        String adjustment = request == null
                ? null
                : request.normalizedAdjustmentInstruction();
        return accepted(aiExecutionService.submitTaskGuidanceRegeneration(
                ownerId(jwt),
                planId,
                versionId,
                itemId,
                adjustment,
                idempotencyKey));
    }

    @GetMapping(ApiConstant.DAILY_PLAN_ITEM_GUIDANCE_CURRENT_EXECUTION)
    @Operation(summary = "Recover the latest Task Guidance AI execution")
    public ApiResponse<AiExecutionResponse> getCurrentExecution(
            @PathVariable UUID planId,
            @PathVariable UUID versionId,
            @PathVariable UUID itemId,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(aiExecutionService.getLatestTaskGuidanceExecution(
                ownerId(jwt),
                planId,
                versionId,
                itemId));
    }

    private ResponseEntity<ApiResponse<AiExecutionResponse>> accepted(
            AiExecutionResponse execution) {
        URI location = URI.create(ApiConstant.AI_EXECUTIONS + "/" + execution.id());
        return ResponseEntity.accepted()
                .location(location)
                .body(ApiResponse.of(execution));
    }

    private UUID ownerId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
