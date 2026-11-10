package com.codegym.aiplanning.controller.roadmap;

import com.codegym.aiplanning.common.api.ApiResponse;
import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.controller.roadmap.dto.CreateLearningUnitRequest;
import com.codegym.aiplanning.controller.roadmap.dto.CreateMilestoneRequest;
import com.codegym.aiplanning.controller.roadmap.dto.CreateRoadmapRequest;
import com.codegym.aiplanning.controller.roadmap.dto.CreateTopicRequest;
import com.codegym.aiplanning.controller.roadmap.dto.RoadmapItemResponse;
import com.codegym.aiplanning.controller.roadmap.dto.RoadmapProgressResponse;
import com.codegym.aiplanning.controller.roadmap.dto.RoadmapResponse;
import com.codegym.aiplanning.controller.roadmap.dto.RoadmapSummaryResponse;
import com.codegym.aiplanning.controller.roadmap.dto.RoadmapVersionResponse;
import com.codegym.aiplanning.controller.roadmap.dto.UpdateRoadmapItemRequest;
import com.codegym.aiplanning.controller.roadmap.dto.UpdateRoadmapRequest;
import com.codegym.aiplanning.entity.roadmap.RoadmapStatus;
import com.codegym.aiplanning.service.roadmap.ManualRoadmapService;
import com.codegym.aiplanning.service.roadmap.RoadmapProgressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstant.ROADMAPS)
@PreAuthorize("hasRole('USER')")
@Tag(name = "Manual Roadmaps", description = "Owner-scoped versioned manual Roadmap authoring")
public class ManualRoadmapController {

    private final ManualRoadmapService manualRoadmapService;
    private final RoadmapProgressService roadmapProgressService;

    public ManualRoadmapController(
            ManualRoadmapService manualRoadmapService,
            RoadmapProgressService roadmapProgressService) {
        this.manualRoadmapService = manualRoadmapService;
        this.roadmapProgressService = roadmapProgressService;
    }

    @PostMapping
    @Operation(summary = "Create a manual Roadmap and RoadmapVersion 1 in DRAFT")
    public ApiResponse<RoadmapResponse> create(
            @Valid @RequestBody CreateRoadmapRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(manualRoadmapService.create(userId(jwt), request));
    }

    @GetMapping
    @Operation(summary = "List the authenticated USER's Roadmaps")
    public ApiResponse<Page<RoadmapSummaryResponse>> list(
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(required = false) RoadmapStatus status,
            @ParameterObject
                    @PageableDefault(
                            size = 20,
                            sort = "updatedAt",
                            direction = org.springframework.data.domain.Sort.Direction.DESC)
                    Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(
                manualRoadmapService.list(userId(jwt), query, status, pageable));
    }

    @GetMapping(ApiConstant.ROADMAP_BY_ID)
    @Operation(summary = "Read an owner-scoped Roadmap and its version history")
    public ApiResponse<RoadmapResponse> get(
            @PathVariable UUID roadmapId, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(manualRoadmapService.get(userId(jwt), roadmapId));
    }

    @PostMapping(ApiConstant.ROADMAP_COPY)
    @Operation(
            summary = "Create an editable copy of an activated Roadmap",
            description = "Copies the active content hierarchy and source links into a new "
                    + "owner-scoped Roadmap with Version 1 in DRAFT. Progress and learning "
                    + "history are not copied.")
    public ApiResponse<RoadmapResponse> createEditableCopy(
            @PathVariable UUID roadmapId,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(
                manualRoadmapService.createEditableCopy(userId(jwt), roadmapId));
    }

    @GetMapping(ApiConstant.ROADMAP_PROGRESS)
    @Operation(summary = "Read progress for the active RoadmapVersion")
    public ApiResponse<RoadmapProgressResponse> getProgress(
            @PathVariable UUID roadmapId, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(
                roadmapProgressService.getProgress(userId(jwt), roadmapId));
    }

    @PatchMapping(ApiConstant.ROADMAP_BY_ID)
    @Operation(
            summary = "Update draft Roadmap title and description",
            description = "An activated Roadmap is immutable. Create an editable Roadmap copy "
                    + "before changing its metadata.")
    public ApiResponse<RoadmapResponse> update(
            @PathVariable UUID roadmapId,
            @Valid @RequestBody UpdateRoadmapRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(
                manualRoadmapService.update(userId(jwt), roadmapId, request));
    }

    @PostMapping(ApiConstant.ROADMAP_VERSIONS)
    @Operation(
            summary = "Initialize a draft version for a Roadmap without content",
            description = "Available only before activation. An activated Roadmap is immutable; "
                    + "create an editable Roadmap copy instead.")
    public ApiResponse<RoadmapVersionResponse> createDraftVersion(
            @PathVariable UUID roadmapId, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(
                manualRoadmapService.createDraftVersion(userId(jwt), roadmapId));
    }

    @GetMapping(ApiConstant.ROADMAP_VERSION_BY_ID)
    @Operation(summary = "Read one exact RoadmapVersion and its complete item hierarchy")
    public ApiResponse<RoadmapVersionResponse> getVersion(
            @PathVariable UUID roadmapId,
            @PathVariable UUID versionId,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(
                manualRoadmapService.getVersion(userId(jwt), roadmapId, versionId));
    }

    @PostMapping(ApiConstant.ROADMAP_VERSION_MILESTONES)
    @Operation(summary = "Add a Milestone to a draft RoadmapVersion")
    public ApiResponse<RoadmapItemResponse> addMilestone(
            @PathVariable UUID roadmapId,
            @PathVariable UUID versionId,
            @Valid @RequestBody CreateMilestoneRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(manualRoadmapService.addMilestone(
                userId(jwt), roadmapId, versionId, request));
    }

    @PostMapping(ApiConstant.ROADMAP_VERSION_TOPICS)
    @Operation(summary = "Add a Topic to a Milestone in a draft RoadmapVersion")
    public ApiResponse<RoadmapItemResponse> addTopic(
            @PathVariable UUID roadmapId,
            @PathVariable UUID versionId,
            @PathVariable UUID milestoneId,
            @Valid @RequestBody CreateTopicRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(manualRoadmapService.addTopic(
                userId(jwt), roadmapId, versionId, milestoneId, request));
    }

    @PostMapping(ApiConstant.ROADMAP_VERSION_LEARNING_UNITS)
    @Operation(summary = "Add an executable Learning Unit to a Topic in a draft RoadmapVersion")
    public ApiResponse<RoadmapItemResponse> addLearningUnit(
            @PathVariable UUID roadmapId,
            @PathVariable UUID versionId,
            @PathVariable UUID topicId,
            @Valid @RequestBody CreateLearningUnitRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(manualRoadmapService.addLearningUnit(
                userId(jwt), roadmapId, versionId, topicId, request));
    }

    @PatchMapping(ApiConstant.ROADMAP_VERSION_ITEM_BY_ID)
    @Operation(summary = "Edit and reorder a draft Roadmap item")
    public ApiResponse<RoadmapItemResponse> updateItem(
            @PathVariable UUID roadmapId,
            @PathVariable UUID versionId,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateRoadmapItemRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(manualRoadmapService.updateItem(
                userId(jwt), roadmapId, versionId, itemId, request));
    }

    @DeleteMapping(ApiConstant.ROADMAP_VERSION_ITEM_BY_ID)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an item from a draft RoadmapVersion")
    public void deleteItem(
            @PathVariable UUID roadmapId,
            @PathVariable UUID versionId,
            @PathVariable UUID itemId,
            @AuthenticationPrincipal Jwt jwt) {
        manualRoadmapService.deleteItem(userId(jwt), roadmapId, versionId, itemId);
    }

    @PostMapping(ApiConstant.ROADMAP_VERSION_ACTIVATE)
    @Operation(summary = "Activate and freeze one exact RoadmapVersion")
    public ApiResponse<RoadmapVersionResponse> activate(
            @PathVariable UUID roadmapId,
            @PathVariable UUID versionId,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(
                manualRoadmapService.activate(userId(jwt), roadmapId, versionId));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
