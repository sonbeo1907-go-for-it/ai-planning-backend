package com.codegym.aiplanning.controller.onboarding;

import com.codegym.aiplanning.common.api.ApiResponse;
import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.controller.onboarding.dto.RoadmapOnboardingResponse;
import com.codegym.aiplanning.controller.onboarding.dto.SaveRoadmapOnboardingRequest;
import com.codegym.aiplanning.service.onboarding.RoadmapOnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstant.ROADMAP_ONBOARDING)
@PreAuthorize("hasRole('USER')")
@Tag(
        name = "Roadmap Onboarding",
        description = "Owner-scoped, resumable inputs for creating a Roadmap draft")
public class RoadmapOnboardingController {

    private final RoadmapOnboardingService onboardingService;

    public RoadmapOnboardingController(RoadmapOnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @PostMapping
    @Operation(
            summary = "Start or resume Roadmap onboarding",
            description = "Returns the authenticated USER's unfinished onboarding or creates a new "
                    + "Roadmap onboarding after profile setup. Completed Roadmaps are never reused.")
    public ApiResponse<RoadmapOnboardingResponse> startOrResume(
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(onboardingService.startOrResume(userId(jwt)));
    }

    @GetMapping(ApiConstant.CURRENT)
    @Operation(summary = "Read the current unfinished Roadmap onboarding")
    public ApiResponse<RoadmapOnboardingResponse> getCurrent(
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(onboardingService.getCurrent(userId(jwt)));
    }

    @GetMapping(ApiConstant.ROADMAP_ONBOARDING_BY_ID)
    @Operation(
            summary = "Read a Roadmap onboarding",
            description = "The Roadmap ID is always scoped to the authenticated owner.")
    public ApiResponse<RoadmapOnboardingResponse> get(
            @PathVariable UUID roadmapId, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(onboardingService.get(userId(jwt), roadmapId));
    }

    @PatchMapping(ApiConstant.ROADMAP_ONBOARDING_BY_ID)
    @Operation(
            summary = "Save partial Roadmap onboarding values",
            description = "Supports Back/Next and later resume without requiring unfinished steps. "
                    + "Goal text is stored as untrusted LearningSource data.")
    public ApiResponse<RoadmapOnboardingResponse> save(
            @PathVariable UUID roadmapId,
            @Valid @RequestBody SaveRoadmapOnboardingRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(onboardingService.save(userId(jwt), roadmapId, request));
    }

    @PostMapping(ApiConstant.ROADMAP_ONBOARDING_COMPLETE)
    @Operation(
            summary = "Complete onboarding and create the Roadmap draft",
            description = "Requires all four survey values. The operation is idempotent and "
                    + "transitions the Roadmap from ONBOARDING to DRAFT without activating it or "
                    + "invoking AI generation.")
    public ApiResponse<RoadmapOnboardingResponse> complete(
            @PathVariable UUID roadmapId, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(onboardingService.complete(userId(jwt), roadmapId));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
