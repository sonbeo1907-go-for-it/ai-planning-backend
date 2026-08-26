package com.codegym.aiplanning.controller.roadmap;

import com.codegym.aiplanning.common.api.ApiResponse;
import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.controller.ai.dto.AiExecutionResponse;
import com.codegym.aiplanning.controller.roadmap.dto.GenerateRoadmapRequest;
import com.codegym.aiplanning.controller.roadmap.dto.RegenerateRoadmapRequest;
import com.codegym.aiplanning.service.ai.execution.AiExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstant.ROADMAPS)
@PreAuthorize("hasRole('USER')")
@Tag(
        name = "AI Master Plan Roadmaps",
        description = "AI-assisted Roadmap generation and regeneration")
public class AiRoadmapController {

    private final AiExecutionService aiExecutionService;

    public AiRoadmapController(AiExecutionService aiExecutionService) {
        this.aiExecutionService = aiExecutionService;
    }

    @PostMapping(ApiConstant.ROADMAP_GENERATE_AI)
    @Operation(
            summary = "Queue AI Master Plan generation",
            description = "Queues generation from the Roadmap goal and optional owner-selected "
                    + "materials. Returns HTTP 202 with an execution ID for status polling; "
                    + "a successful execution references the new DRAFT RoadmapVersion.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "202",
            description = "Generation was queued or an existing active execution was returned")
    public ResponseEntity<ApiResponse<AiExecutionResponse>> generate(
            @PathVariable UUID roadmapId,
            @Valid @RequestBody(required = false) GenerateRoadmapRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false)
                    String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        AiExecutionResponse execution = aiExecutionService.submitRoadmapGeneration(
                userId,
                roadmapId,
                request == null ? List.of() : request.normalizedMaterialIds(),
                idempotencyKey);
        return accepted(execution);
    }

    @PostMapping(ApiConstant.ROADMAP_REGENERATE_AI)
    @Operation(
            summary = "Queue AI Master Plan regeneration",
            description = "Queues creation of a new DRAFT version using optional adjustment "
                    + "instructions while preserving previous versions. Returns HTTP 202 with "
                    + "an execution ID for status polling.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "202",
            description = "Regeneration was queued or an existing active execution was returned")
    public ResponseEntity<ApiResponse<AiExecutionResponse>> regenerate(
            @PathVariable UUID roadmapId,
            @Valid @RequestBody(required = false) RegenerateRoadmapRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false)
                    String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        String prompt = request != null ? request.adjustmentPrompt() : null;
        AiExecutionResponse execution = aiExecutionService.submitRoadmapRegeneration(
                userId, roadmapId, prompt, idempotencyKey);
        return accepted(execution);
    }

    @GetMapping(ApiConstant.ROADMAP_CURRENT_AI_EXECUTION)
    @Operation(
            summary = "Get the latest Roadmap AI execution",
            description = "Allows the Roadmap owner to resume status polling after navigation or reload.")
    public ApiResponse<AiExecutionResponse> getLatestExecution(
            @PathVariable UUID roadmapId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ApiResponse.of(
                aiExecutionService.getLatestRoadmapExecution(userId, roadmapId));
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
