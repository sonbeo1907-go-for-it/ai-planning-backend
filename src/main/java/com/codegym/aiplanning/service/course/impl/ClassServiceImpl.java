package com.codegym.aiplanning.service.course.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.admin.dto.course.ClassResponse;
import com.codegym.aiplanning.controller.admin.dto.course.CreateClassRequest;
import com.codegym.aiplanning.entity.course.StudyClass;
import com.codegym.aiplanning.repository.course.CourseRepository;
import com.codegym.aiplanning.repository.course.StudyClassRepository;
import com.codegym.aiplanning.service.course.ClassService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassServiceImpl implements ClassService {

    private final StudyClassRepository studyClassRepository;
    private final CourseRepository courseRepository;

    public ClassServiceImpl(StudyClassRepository studyClassRepository, CourseRepository courseRepository) {
        this.studyClassRepository = studyClassRepository;
        this.courseRepository = courseRepository;
    }

    @Override
    @Transactional
    public ClassResponse createClass(CreateClassRequest request) {
        if (!courseRepository.existsById(request.courseId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Course not found");
        }

        if (studyClassRepository.existsByCodeIgnoreCase(request.code())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Class code already exists");
        }

        StudyClass studyClass = StudyClass.create(
                request.courseId(),
                request.code(),
                request.name(),
                request.description(),
                request.status()
        );

        studyClass = studyClassRepository.save(studyClass);
        return ClassResponse.from(studyClass);
    }
}
