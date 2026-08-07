package com.codegym.aiplanning.controller.admin;

import com.codegym.aiplanning.common.api.ApiResponse;
import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.controller.admin.dto.course.ClassResponse;
import com.codegym.aiplanning.controller.admin.dto.course.CreateClassRequest;
import com.codegym.aiplanning.service.course.ClassService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.codegym.aiplanning.controller.admin.dto.course.UpdateClassRequest;
import com.codegym.aiplanning.controller.admin.dto.course.ChangeClassStatusRequest;
import java.util.UUID;

@RestController
@RequestMapping(ApiConstant.ADMIN_CLASSES)
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Class", description = "Class management for administrators")
public class AdminClassController {

    private final ClassService classService;

    public AdminClassController(ClassService classService) {
        this.classService = classService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new class for a course")
    public ApiResponse<ClassResponse> createClass(@Valid @RequestBody CreateClassRequest request) {
        return ApiResponse.of(classService.createClass(request));
    }

    @GetMapping
    @Operation(summary = "Get all classes with pagination")
    public ApiResponse<Page<ClassResponse>> getAllClasses(@ParameterObject Pageable pageable) {
        return ApiResponse.of(classService.getAllClasses(pageable));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing class")
    public ApiResponse<ClassResponse> updateClass(@PathVariable UUID id, @Valid @RequestBody UpdateClassRequest request) {
        return ApiResponse.of(classService.updateClass(id, request));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Change the status of a class according to its lifecycle")
    public ApiResponse<ClassResponse> changeClassStatus(@PathVariable UUID id, @Valid @RequestBody ChangeClassStatusRequest request) {
        return ApiResponse.of(classService.changeStatus(id, request));
    }
}
