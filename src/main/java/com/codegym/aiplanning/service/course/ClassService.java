package com.codegym.aiplanning.service.course;

import com.codegym.aiplanning.controller.admin.dto.course.ClassResponse;
import com.codegym.aiplanning.controller.admin.dto.course.CreateClassRequest;

public interface ClassService {
    ClassResponse createClass(CreateClassRequest request);
}
