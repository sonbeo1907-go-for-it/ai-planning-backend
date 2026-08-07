package com.codegym.aiplanning.service.course;

import com.codegym.aiplanning.controller.admin.dto.course.ClassResponse;
import com.codegym.aiplanning.controller.admin.dto.course.CreateClassRequest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;
import com.codegym.aiplanning.controller.admin.dto.course.UpdateClassRequest;
import com.codegym.aiplanning.controller.admin.dto.course.ChangeClassStatusRequest;

public interface ClassService {
    Page<ClassResponse> getAllClasses(Pageable pageable);
    ClassResponse createClass(CreateClassRequest request);
    ClassResponse updateClass(UUID id, UpdateClassRequest request);
    ClassResponse changeStatus(UUID id, ChangeClassStatusRequest request);
}
