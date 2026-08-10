package com.codegym.aiplanning.controller.admin;

import com.codegym.aiplanning.common.api.ApiError;
import com.codegym.aiplanning.common.api.ApiResponse;
import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.common.api.PageResponse;
import com.codegym.aiplanning.controller.course.dto.ChangeClassStatusRequest;
import com.codegym.aiplanning.controller.course.dto.ClassResponse;
import com.codegym.aiplanning.controller.course.dto.CreateClassRequest;
import com.codegym.aiplanning.controller.course.dto.UpdateClassRequest;
import com.codegym.aiplanning.service.course.ClassCreationService;
import com.codegym.aiplanning.service.course.ClassQueryService;
import com.codegym.aiplanning.service.course.ClassStatusService;
import com.codegym.aiplanning.service.course.ClassUpdateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import java.util.UUID;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstant.ADMIN_CLASSES)
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Class Management", description = "Admin API for managing classes (US-CLS-01 to US-CLS-03)")
public class AdminClassController {

    private final ClassCreationService classCreationService;
    private final ClassUpdateService classUpdateService;
    private final ClassQueryService classQueryService;
    private final ClassStatusService classStatusService;

    public AdminClassController(
            ClassCreationService classCreationService, 
            ClassUpdateService classUpdateService, 
            ClassQueryService classQueryService,
            ClassStatusService classStatusService) {
        this.classCreationService = classCreationService;
        this.classUpdateService = classUpdateService;
        this.classQueryService = classQueryService;
        this.classStatusService = classStatusService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new class for a course")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201", description = "Class created successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Invalid input data",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Course not found",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "Class code already exists",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ApiResponse<ClassResponse> createClass(
            @Valid @RequestBody CreateClassRequest request, @AuthenticationPrincipal Jwt jwt) {
        ClassResponse response = classCreationService.createClass(request, jwt);
        return ApiResponse.of(response);
    }

    @GetMapping
    @Operation(summary = "Get list of classes with pagination")
    public ApiResponse<PageResponse<ClassResponse>> getClasses(@Valid @ModelAttribute com.codegym.aiplanning.controller.course.dto.ClassSearchParam param) {
        return ApiResponse.of(classQueryService.getClasses(param));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update mutable details of a class")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200", description = "Class updated successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Invalid input data or invalid class dates",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Class not found",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "Concurrent modification (optimistic locking failed)",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ApiResponse<ClassResponse> updateClass(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateClassRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        ClassResponse response = classUpdateService.updateClass(id, request, jwt);
        return ApiResponse.of(response);
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Change the status of a class")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Status changed successfully",
                content = @Content(schema = @Schema(implementation = ClassResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Invalid status transition",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Class not found",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "Concurrent modification",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ApiResponse<ClassResponse> changeStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeClassStatusRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        ClassResponse response = classStatusService.changeStatus(id, request, jwt);
        return ApiResponse.of(response);
    }
}
