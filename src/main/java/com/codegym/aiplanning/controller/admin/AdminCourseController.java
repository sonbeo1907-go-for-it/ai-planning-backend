package com.codegym.aiplanning.controller.admin;

import com.codegym.aiplanning.common.api.ApiResponse;
import com.codegym.aiplanning.common.api.PageResponse;
import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.controller.admin.dto.course.CourseResponse;
import com.codegym.aiplanning.controller.admin.dto.course.CreateCourseRequest;
import com.codegym.aiplanning.service.course.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstant.ADMIN_COURSES)
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Course", description = "Course management for administrators")
public class AdminCourseController {

    private final CourseService courseService;

    public AdminCourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new course")
    public ApiResponse<CourseResponse> createCourse(@Valid @RequestBody CreateCourseRequest request) {
        return ApiResponse.of(courseService.createCourse(request));
    }

    @GetMapping
    @Operation(summary = "Get all courses with pagination")
    public ApiResponse<PageResponse<CourseResponse>> getAllCourses(@PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.of(PageResponse.from(courseService.getAllCourses(pageable)));
    }
}
