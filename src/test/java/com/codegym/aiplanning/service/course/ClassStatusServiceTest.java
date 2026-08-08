package com.codegym.aiplanning.service.course;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.course.dto.ChangeClassStatusRequest;
import com.codegym.aiplanning.controller.course.dto.ClassResponse;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.course.ClassStatus;
import com.codegym.aiplanning.entity.course.StudyClass;
import com.codegym.aiplanning.repository.course.StudyClassRepository;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.service.course.impl.ClassStatusServiceImpl;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ClassStatusServiceTest {

    @Mock
    private StudyClassRepository studyClassRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private ClassStatusServiceImpl classStatusService;

    private UUID classId;
    private Jwt actorJwt;
    private StudyClass studyClass;

    @BeforeEach
    void setUp() {
        classId = UUID.randomUUID();
        actorJwt = mock(Jwt.class);
        when(actorJwt.getSubject()).thenReturn(UUID.randomUUID().toString());
        when(actorJwt.getClaimAsString("preferred_username")).thenReturn("admin");

        studyClass = StudyClass.create(UUID.randomUUID(), "CLASS-1", "Name", "Desc", UUID.randomUUID());
        ReflectionTestUtils.setField(studyClass, "id", classId);
        // Default status is PLANNED
    }

    @Test
    void changeStatus_PlannedToActive_Success() {
        ChangeClassStatusRequest request = new ChangeClassStatusRequest(ClassStatus.ACTIVE, 0L);
        when(studyClassRepository.findById(classId)).thenReturn(Optional.of(studyClass));

        assertNull(studyClass.getOpenedAt());

        ClassResponse response = classStatusService.changeStatus(classId, request, actorJwt);

        assertEquals(ClassStatus.ACTIVE, response.status());
        assertNotNull(studyClass.getOpenedAt());
        verify(auditLogService).logAction(any(), any(), eq(AuditEventAction.CLASS_STATUS_CHANGED), any(), any(), any(), any(), any());
    }

    @Test
    void changeStatus_ActiveToClosed_Success() {
        studyClass.changeStatus(ClassStatus.ACTIVE, UUID.randomUUID());
        
        ChangeClassStatusRequest request = new ChangeClassStatusRequest(ClassStatus.CLOSED, 0L);
        when(studyClassRepository.findById(classId)).thenReturn(Optional.of(studyClass));

        assertNull(studyClass.getClosedAt());

        ClassResponse response = classStatusService.changeStatus(classId, request, actorJwt);

        assertEquals(ClassStatus.CLOSED, response.status());
        assertNotNull(studyClass.getClosedAt());
    }

    @Test
    void changeStatus_ConcurrentModification_ThrowsException() {
        ChangeClassStatusRequest request = new ChangeClassStatusRequest(ClassStatus.ACTIVE, 1L); // Version mismatch
        when(studyClassRepository.findById(classId)).thenReturn(Optional.of(studyClass));

        BusinessException exception = assertThrows(BusinessException.class, () -> 
            classStatusService.changeStatus(classId, request, actorJwt));

        assertEquals(ErrorCode.CONCURRENT_MODIFICATION, exception.errorCode());
    }

    @Test
    void changeStatus_InvalidTransition_ThrowsException() {
        ChangeClassStatusRequest request = new ChangeClassStatusRequest(ClassStatus.CLOSED, 0L); // PLANNED -> CLOSED is invalid
        when(studyClassRepository.findById(classId)).thenReturn(Optional.of(studyClass));

        BusinessException exception = assertThrows(BusinessException.class, () -> 
            classStatusService.changeStatus(classId, request, actorJwt));

        assertEquals(ErrorCode.INVALID_STATUS_TRANSITION, exception.errorCode());
    }
}
