package com.codegym.aiplanning.service.course;

import com.codegym.aiplanning.controller.admin.dto.course.CourseResponse;
import com.codegym.aiplanning.controller.admin.dto.course.CreateCourseRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CourseService {
    CourseResponse createCourse(CreateCourseRequest request);
    Page<CourseResponse> getAllCourses(Pageable pageable);
}
