package com.codegym.aiplanning.controller.roadmap;

import com.codegym.aiplanning.common.api.ApiResponse;
import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.controller.roadmap.dto.GenerateRoadmapRequest;
import com.codegym.aiplanning.controller.roadmap.dto.RegenerateRoadmapRequest;
import com.codegym.aiplanning.controller.roadmap.dto.RoadmapVersionResponse;
import com.codegym.aiplanning.service.roadmap.AiRoadmapGeneratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    private final AiRoadmapGeneratorService aiRoadmapGeneratorService;

    public AiRoadmapController(AiRoadmapGeneratorService aiRoadmapGeneratorService) {
        this.aiRoadmapGeneratorService = aiRoadmapGeneratorService;
    }

    @PostMapping(ApiConstant.ROADMAP_GENERATE_AI)
    @Operation(
            summary = "Generate an AI Master Plan draft",
            description = "Uses the Roadmap goal and optional owner-selected materials "
                    + "to create a new DRAFT version.")
    public ApiResponse<RoadmapVersionResponse> generate(
            @PathVariable UUID roadmapId,
            @Valid @RequestBody(required = false) GenerateRoadmapRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ApiResponse.of(aiRoadmapGeneratorService.generate(
                userId,
                roadmapId,
                request == null ? List.of() : request.normalizedMaterialIds()));
    }

    @PostMapping(ApiConstant.ROADMAP_REGENERATE_AI)
    @Operation(
            summary = "Regenerate an AI Master Plan draft",
            description = "Creates a new DRAFT version with optional adjustment instructions "
                    + "while preserving previous versions.")
    public ApiResponse<RoadmapVersionResponse> regenerate(
            @PathVariable UUID roadmapId,
            @Valid @RequestBody(required = false) RegenerateRoadmapRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        String prompt = request != null ? request.adjustmentPrompt() : null;
        return ApiResponse.of(aiRoadmapGeneratorService.regenerate(userId, roadmapId, prompt));
    }
}
