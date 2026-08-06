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
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.controller.admin.dto.course.UpdateClassRequest;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
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

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ClassServiceImpl classService;

    private CreateClassRequest request;
    private UpdateClassRequest updateRequest;
    private StudyClass studyClass;
    private final UUID courseId = UUID.randomUUID();
    private final UUID classId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        request = new CreateClassRequest(courseId, "C101", "Class 101", "Basic Class", ClassStatus.PLANNED);
        updateRequest = new UpdateClassRequest("Class 101 Updated", "New Desc", Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS), 1L);
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

    @Test
    void updateClass_WhenValid_ShouldUpdateAndAudit() throws Exception {
        when(studyClassRepository.findById(classId)).thenReturn(Optional.of(studyClass));
        when(studyClassRepository.saveAndFlush(any(StudyClass.class))).thenReturn(studyClass);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        ClassResponse response = classService.updateClass(classId, updateRequest);

        assertNotNull(response);
        assertEquals("Class 101 Updated", studyClass.getName());
        verify(studyClassRepository).saveAndFlush(any(StudyClass.class));
        verify(auditLogService).logAction(eq(null), eq("system"), eq(AuditEventAction.UPDATE_CLASS), eq("classes"), eq(classId.toString()), anyString());
    }

    @Test
    void updateClass_WhenDatesInvalid_ShouldThrowException() {
        UpdateClassRequest invalidRequest = new UpdateClassRequest("Name", "Desc", Instant.now().plus(1, ChronoUnit.DAYS), Instant.now(), 1L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> classService.updateClass(classId, invalidRequest));

        assertEquals(ErrorCode.VALIDATION_FAILED, exception.errorCode());
        verify(studyClassRepository, never()).findById(any());
    }

    @Test
    void updateClass_WhenOptimisticLockingFails_ShouldThrowException() throws Exception {
        when(studyClassRepository.findById(classId)).thenReturn(Optional.of(studyClass));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(studyClassRepository.saveAndFlush(any(StudyClass.class)))
                .thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(StudyClass.class, classId));

        assertThrows(
                org.springframework.orm.ObjectOptimisticLockingFailureException.class,
                () -> classService.updateClass(classId, updateRequest));
    }
}
