package com.codegym.aiplanning.service.course;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.course.dto.ClassResponse;
import com.codegym.aiplanning.controller.course.dto.CreateClassRequest;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.course.ClassStatus;
import com.codegym.aiplanning.entity.course.StudyClass;
import com.codegym.aiplanning.repository.course.StudyClassRepository;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.service.course.impl.ClassCreationServiceImpl;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class ClassCreationServiceTest {

    @Mock
    private StudyClassValidator validator;

    @Mock
    private StudyClassRepository studyClassRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private ClassCreationServiceImpl classCreationService;

    private Jwt actorJwt;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        actorId = UUID.randomUUID();
        actorJwt = mock(Jwt.class);
        when(actorJwt.getSubject()).thenReturn(actorId.toString());
        when(actorJwt.getClaimAsString("preferred_username")).thenReturn("admin_user");
    }

    @Test
    void createClass_Success() {
        // Arrange
        UUID courseId = UUID.randomUUID();
        CreateClassRequest request = new CreateClassRequest(courseId, "  jav101-01  ", " Java Basic ", " Desc ");

        StudyClass savedClass = mock(StudyClass.class);
        UUID savedId = UUID.randomUUID();
        when(savedClass.getId()).thenReturn(savedId);
        when(savedClass.getCode()).thenReturn("JAV101-01");
        when(savedClass.getName()).thenReturn("Java Basic");
        when(savedClass.getStatus()).thenReturn(ClassStatus.PLANNED);

        when(studyClassRepository.saveAndFlush(any(StudyClass.class))).thenReturn(savedClass);

        // Act
        ClassResponse response = classCreationService.createClass(request, actorJwt);

        // Assert
        assertNotNull(response);
        assertEquals("JAV101-01", response.code());
        assertEquals(ClassStatus.PLANNED, response.status());

        verify(validator).validateCourseExists(courseId);
        verify(validator).validateDuplicateCode("  jav101-01  ");
        verify(studyClassRepository).saveAndFlush(any(StudyClass.class));
        verify(auditLogService).logAction(
                eq(actorId),
                eq("admin_user"),
                eq(AuditEventAction.CLASS_CREATED),
                eq("CLASS"),
                eq(savedId.toString()),
                anyString(),
                any(),
                any());
    }

    @Test
    void createClass_CodeUppercaseAndTrimmed() {
        // Arrange
        UUID courseId = UUID.randomUUID();
        CreateClassRequest request = new CreateClassRequest(courseId, " c0523g1 ", "Name", null);

        StudyClass savedClass = mock(StudyClass.class);
        when(savedClass.getId()).thenReturn(UUID.randomUUID());
        when(savedClass.getCode()).thenReturn("C0523G1");
        when(studyClassRepository.saveAndFlush(any(StudyClass.class))).thenAnswer(invocation -> {
            StudyClass arg = invocation.getArgument(0);
            assertEquals("C0523G1", arg.getCode()); // Assert inside mock to verify conversion
            assertEquals(ClassStatus.PLANNED, arg.getStatus());
            return savedClass;
        });

        // Act
        classCreationService.createClass(request, actorJwt);
    }

    @Test
    void createClass_ThrowsCourseNotFound() {
        // Arrange
        UUID courseId = UUID.randomUUID();
        CreateClassRequest request = new CreateClassRequest(courseId, "CODE", "Name", null);

        doThrow(new BusinessException(ErrorCode.COURSE_NOT_FOUND, "Not found"))
                .when(validator).validateCourseExists(courseId);

        // Act & Assert
        BusinessException exception = assertThrows(
                BusinessException.class, () -> classCreationService.createClass(request, actorJwt));
        assertEquals(ErrorCode.COURSE_NOT_FOUND, exception.errorCode());
        verifyNoInteractions(studyClassRepository);
    }

    @Test
    void createClass_ThrowsClassCodeAlreadyExists() {
        // Arrange
        UUID courseId = UUID.randomUUID();
        CreateClassRequest request = new CreateClassRequest(courseId, "CODE", "Name", null);

        doThrow(new BusinessException(ErrorCode.CLASS_CODE_ALREADY_EXISTS, "Exists"))
                .when(validator).validateDuplicateCode("CODE");

        // Act & Assert
        BusinessException exception = assertThrows(
                BusinessException.class, () -> classCreationService.createClass(request, actorJwt));
        assertEquals(ErrorCode.CLASS_CODE_ALREADY_EXISTS, exception.errorCode());
        verifyNoInteractions(studyClassRepository);
    }

    @Test
    void createClass_ThrowsDataIntegrityViolation() {
        // Arrange
        UUID courseId = UUID.randomUUID();
        CreateClassRequest request = new CreateClassRequest(courseId, "CODE", "Name", null);

        when(studyClassRepository.saveAndFlush(any(StudyClass.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate"));

        // Act & Assert
        BusinessException exception = assertThrows(
                BusinessException.class, () -> classCreationService.createClass(request, actorJwt));
        assertEquals(ErrorCode.CLASS_CODE_ALREADY_EXISTS, exception.errorCode());
    }
}
