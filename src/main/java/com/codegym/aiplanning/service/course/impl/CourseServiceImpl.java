package com.codegym.aiplanning.service.course.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.admin.dto.course.CourseResponse;
import com.codegym.aiplanning.controller.admin.dto.course.CreateCourseRequest;
import com.codegym.aiplanning.entity.course.Course;
import com.codegym.aiplanning.repository.course.CourseRepository;
import com.codegym.aiplanning.service.course.CourseService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseServiceImpl implements CourseService {

    private final CourseRepository courseRepository;

    public CourseServiceImpl(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @Override
    @Transactional
    public CourseResponse createCourse(CreateCourseRequest request) {
        if (courseRepository.existsByCodeIgnoreCase(request.code())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Course code already exists");
        }

        Course course = Course.create(
                request.code(),
                request.name(),
                request.description(),
                request.status()
        );

        course = courseRepository.save(course);
        return CourseResponse.from(course);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CourseResponse> getAllCourses(Pageable pageable) {
        return courseRepository.findAll(pageable).map(CourseResponse::from);
    }
}
