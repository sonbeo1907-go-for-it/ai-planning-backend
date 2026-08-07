package com.codegym.aiplanning.controller.curriculum;

import com.codegym.aiplanning.common.api.ApiError;
import com.codegym.aiplanning.common.api.ApiResponse;
import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.controller.curriculum.dto.CreateModuleRequest;
import com.codegym.aiplanning.controller.curriculum.dto.ModuleResponse;
import com.codegym.aiplanning.controller.curriculum.dto.ReorderModulesRequest;
import com.codegym.aiplanning.controller.curriculum.dto.UpdateModuleRequest;
import com.codegym.aiplanning.service.curriculum.ModuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstant.COURSES)
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Curriculum Module Management", description = "Admin module management for course curriculum (US-MOD-01)")
public class ModuleController {

    private final ModuleService moduleService;

    public ModuleController(ModuleService moduleService) {
        this.moduleService = moduleService;
    }

    @PostMapping(ApiConstant.COURSE_MODULES)
    @Operation(summary = "Add a module to a course (US-MOD-01)", description = "Adds a new module into a course roadmap.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200", description = "Module created successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Validation failed",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Course not found",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "Module code already exists in this course",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ApiResponse<ModuleResponse> createModule(
            @PathVariable UUID courseId,
            @Valid @RequestBody CreateModuleRequest request,
            @AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(moduleService.createModule(courseId, request, actorJwt));
    }

    @GetMapping(ApiConstant.COURSE_MODULES)
    @Operation(summary = "Get all modules of a course", description = "Returns modules ordered by sequence number.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200", description = "Modules retrieved successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Course not found",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ApiResponse<List<ModuleResponse>> getModulesByCourseId(@PathVariable UUID courseId) {
        return ApiResponse.of(moduleService.getModulesByCourseId(courseId));
    }

    @PatchMapping(ApiConstant.MODULE_BY_ID)
    @Operation(summary = "Update module name & description (US-MOD-02)", description = "Updates module name and description. Code and sequence remain intact.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200", description = "Module updated successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Validation failed",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Module or Course not found",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ApiResponse<ModuleResponse> updateModule(
            @PathVariable UUID courseId,
            @PathVariable UUID moduleId,
            @Valid @RequestBody UpdateModuleRequest request,
            @AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(moduleService.updateModule(courseId, moduleId, request, actorJwt));
    }

    @PutMapping(ApiConstant.COURSE_MODULES_REORDER)
    @Operation(summary = "Reorder modules sequence numbers (US-MOD-03)", description = "Reorders sequence numbers of modules in a course.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200", description = "Modules reordered successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Validation failed or duplicate sequence numbers",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Module or Course not found",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ApiResponse<List<ModuleResponse>> reorderModules(
            @PathVariable UUID courseId,
            @Valid @RequestBody ReorderModulesRequest request,
            @AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(moduleService.reorderModules(courseId, request, actorJwt));
    }

    @PostMapping(ApiConstant.MODULE_ACTIVATE)
    @Operation(summary = "Activate module (US-MOD-04)", description = "Activates a module in a course.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200", description = "Module activated successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Module or Course not found",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ApiResponse<ModuleResponse> activateModule(
            @PathVariable UUID courseId,
            @PathVariable UUID moduleId,
            @AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(moduleService.activateModule(courseId, moduleId, actorJwt));
    }

    @PostMapping(ApiConstant.MODULE_DEACTIVATE)
    @Operation(summary = "Deactivate module (US-MOD-04)", description = "Deactivates a module without deleting historical data.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200", description = "Module deactivated successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Module or Course not found",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ApiResponse<ModuleResponse> deactivateModule(
            @PathVariable UUID courseId,
            @PathVariable UUID moduleId,
            @AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(moduleService.deactivateModule(courseId, moduleId, actorJwt));
    }
}
