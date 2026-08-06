package com.codegym.aiplanning.service.course.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.admin.dto.course.ClassResponse;
import com.codegym.aiplanning.controller.admin.dto.course.CreateClassRequest;
import com.codegym.aiplanning.entity.course.ClassStatus;
import com.codegym.aiplanning.entity.course.StudyClass;
import com.codegym.aiplanning.repository.course.CourseRepository;
import com.codegym.aiplanning.repository.course.StudyClassRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ClassServiceImplTest {

    @Mock
    private StudyClassRepository studyClassRepository;

    @Mock
    private CourseRepository courseRepository;

    @InjectMocks
    private ClassServiceImpl classService;

    private CreateClassRequest request;
    private StudyClass studyClass;
    private final UUID courseId = UUID.randomUUID();
    private final UUID classId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        request = new CreateClassRequest(courseId, "C101", "Class 101", "Basic Class", ClassStatus.PLANNED);
        studyClass = StudyClass.create(courseId, "C101", "Class 101", "Basic Class", ClassStatus.PLANNED);
        ReflectionTestUtils.setField(studyClass, "id", classId);
    }

    @Test
    void createClass_WhenValid_ShouldCreateClass() {
        when(courseRepository.existsById(courseId)).thenReturn(true);
        when(studyClassRepository.existsByCodeIgnoreCase("C101")).thenReturn(false);
        when(studyClassRepository.save(any(StudyClass.class))).thenReturn(studyClass);

        ClassResponse response = classService.createClass(request);

        assertNotNull(response);
        assertEquals(classId, response.id());
        assertEquals("C101", response.code());
        assertEquals(courseId, response.courseId());
        verify(studyClassRepository).save(any(StudyClass.class));
    }

    @Test
    void createClass_WhenCourseNotFound_ShouldThrowException() {
        when(courseRepository.existsById(courseId)).thenReturn(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> classService.createClass(request));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.errorCode());
        verify(studyClassRepository, never()).save(any(StudyClass.class));
    }

    @Test
    void createClass_WhenCodeExists_ShouldThrowException() {
        when(courseRepository.existsById(courseId)).thenReturn(true);
        when(studyClassRepository.existsByCodeIgnoreCase("C101")).thenReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> classService.createClass(request));

        assertEquals(ErrorCode.CONFLICT, exception.errorCode());
        verify(studyClassRepository, never()).save(any(StudyClass.class));
    }
}
