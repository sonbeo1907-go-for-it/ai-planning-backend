package com.codegym.aiplanning.service.course;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.repository.course.CourseRepository;
import com.codegym.aiplanning.repository.course.StudyClassRepository;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class StudyClassValidator {

    private final CourseRepository courseRepository;
    private final StudyClassRepository studyClassRepository;

    public StudyClassValidator(
            CourseRepository courseRepository, StudyClassRepository studyClassRepository) {
        this.courseRepository = courseRepository;
        this.studyClassRepository = studyClassRepository;
    }

    public void validateCourseExists(UUID courseId) {
        if (!courseRepository.existsById(courseId)) {
            throw new BusinessException(
                    ErrorCode.COURSE_NOT_FOUND, "Course not found with id: " + courseId);
        }
    }

    public void validateDuplicateCode(String code) {
        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
        if (studyClassRepository.existsByCode(normalizedCode)) {
            throw new BusinessException(
                    ErrorCode.CLASS_CODE_ALREADY_EXISTS,
                    "A class with code '" + normalizedCode + "' already exists.");
        }
    }
}
