package com.codegym.aiplanning.controller.course;

import com.codegym.aiplanning.common.api.ApiError;
import com.codegym.aiplanning.common.api.ApiResponse;
import com.codegym.aiplanning.common.api.PageResponse;
import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.controller.course.dto.CourseResponse;
import com.codegym.aiplanning.controller.course.dto.CourseSearchParam;
import com.codegym.aiplanning.controller.course.dto.CreateCourseRequest;
import com.codegym.aiplanning.controller.course.dto.UpdateCourseRequest;
import com.codegym.aiplanning.service.course.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstant.COURSES)
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Course Management", description = "Admin course management (US-CUR-01 to US-CUR-04)")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @PostMapping(ApiConstant.COURSE_COLLECTION)
    @Operation(summary = "Create a course")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200", description = "Course created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Request validation failed",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "Course code already exists",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ApiResponse<CourseResponse> createCourse(
            @Valid @RequestBody CreateCourseRequest request,
            @AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(courseService.createCourse(request, actorJwt));
    }

    @GetMapping(ApiConstant.COURSE_COLLECTION)
    @Operation(summary = "List and search courses")
    public ApiResponse<PageResponse<CourseResponse>> getCourses(
            @Valid @ModelAttribute CourseSearchParam param) {
        return ApiResponse.of(courseService.getCourses(param));
    }

    @GetMapping(ApiConstant.COURSE_BY_ID)
    @Operation(summary = "Read a course")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200", description = "Course found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Course not found",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ApiResponse<CourseResponse> getCourse(@PathVariable UUID courseId) {
        return ApiResponse.of(courseService.getCourse(courseId));
    }

    @PatchMapping(ApiConstant.COURSE_BY_ID)
    @Operation(
            summary = "Update a course",
            description = "Updates name and description. The business code is immutable.")
    public ApiResponse<CourseResponse> updateCourse(
            @PathVariable UUID courseId,
            @Valid @RequestBody UpdateCourseRequest request,
            @AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(courseService.updateCourse(courseId, request, actorJwt));
    }

    @PostMapping(ApiConstant.COURSE_DEACTIVATE)
    @Operation(
            summary = "Deactivate a course",
            description = "Stops future class and enrollment creation without deleting history.")
    public ApiResponse<CourseResponse> deactivateCourse(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(courseService.deactivateCourse(courseId, actorJwt));
    }

    @PostMapping(ApiConstant.COURSE_ACTIVATE)
    @Operation(summary = "Activate a course")
    public ApiResponse<CourseResponse> activateCourse(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(courseService.activateCourse(courseId, actorJwt));
    }
}
