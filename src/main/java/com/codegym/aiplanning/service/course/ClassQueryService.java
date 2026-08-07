package com.codegym.aiplanning.service.course;

import com.codegym.aiplanning.common.api.PageResponse;
import com.codegym.aiplanning.controller.course.dto.ClassResponse;
import org.springframework.data.domain.Pageable;

public interface ClassQueryService {
    PageResponse<ClassResponse> getClasses(Pageable pageable);
}
