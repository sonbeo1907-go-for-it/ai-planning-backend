package com.codegym.aiplanning.service.course.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.admin.dto.course.CourseResponse;
import com.codegym.aiplanning.controller.admin.dto.course.CreateCourseRequest;
import com.codegym.aiplanning.entity.course.Course;
import com.codegym.aiplanning.entity.course.CourseStatus;
import com.codegym.aiplanning.repository.course.CourseRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CourseServiceImplTest {

    @Mock
    private CourseRepository courseRepository;

    @InjectMocks
    private CourseServiceImpl courseService;

    private CreateCourseRequest request;
    private Course course;
    private final UUID courseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        request = new CreateCourseRequest("J101", "Java 101", "Basic Java", CourseStatus.ACTIVE);
        course = Course.create("J101", "Java 101", "Basic Java", CourseStatus.ACTIVE);
        ReflectionTestUtils.setField(course, "id", courseId);
    }

    @Test
    void createCourse_WhenCodeDoesNotExist_ShouldCreateCourse() {
        when(courseRepository.existsByCodeIgnoreCase("J101")).thenReturn(false);
        when(courseRepository.save(any(Course.class))).thenReturn(course);

        CourseResponse response = courseService.createCourse(request);

        assertNotNull(response);
        assertEquals(courseId, response.id());
        assertEquals("J101", response.code());
        assertEquals("Java 101", response.name());
        verify(courseRepository).save(any(Course.class));
    }

    @Test
    void createCourse_WhenCodeExists_ShouldThrowException() {
        when(courseRepository.existsByCodeIgnoreCase("J101")).thenReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> courseService.createCourse(request));

        assertEquals(ErrorCode.CONFLICT, exception.errorCode());
        verify(courseRepository, never()).save(any(Course.class));
    }
}
