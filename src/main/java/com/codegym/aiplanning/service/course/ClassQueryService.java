package com.codegym.aiplanning.service.course;

import com.codegym.aiplanning.common.api.PageResponse;
import com.codegym.aiplanning.controller.course.dto.ClassResponse;
import com.codegym.aiplanning.controller.course.dto.ClassSearchParam;

public interface ClassQueryService {
    PageResponse<ClassResponse> getClasses(ClassSearchParam param);
}
