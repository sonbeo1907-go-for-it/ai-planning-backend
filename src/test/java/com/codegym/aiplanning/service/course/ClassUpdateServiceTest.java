package com.codegym.aiplanning.service.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.course.dto.ClassResponse;
import com.codegym.aiplanning.controller.course.dto.UpdateClassRequest;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.course.StudyClass;
import com.codegym.aiplanning.repository.course.StudyClassRepository;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.service.course.impl.ClassUpdateServiceImpl;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ClassUpdateServiceTest {

    @Mock
    private StudyClassRepository studyClassRepository;

    @Mock
    private AuditLogService auditLogService;

    private ClassUpdateService classUpdateService;

    private Jwt actorJwt;
    private UUID actorId;
    private UUID classId;
    private StudyClass existingClass;
    private UUID courseId;

    @BeforeEach
    void setUp() {
        classUpdateService = new ClassUpdateServiceImpl(studyClassRepository, auditLogService);
        actorId = UUID.randomUUID();
        actorJwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", actorId.toString())
                .claim("preferred_username", "admin")
                .build();
        classId = UUID.randomUUID();
        courseId = UUID.randomUUID();
        existingClass = StudyClass.create(courseId, "JAVA-01", "Old Name", "Old Description", actorId);
        ReflectionTestUtils.setField(existingClass, "id", classId);
    }

    @Test
    void updateClass_Success() {
        UpdateClassRequest request = new UpdateClassRequest("New Name", "New Description", null, null, 0L);
        when(studyClassRepository.findById(classId)).thenReturn(Optional.of(existingClass));

        ClassResponse response = classUpdateService.updateClass(classId, request, actorJwt);

        assertThat(response.name()).isEqualTo("New Name");
        assertThat(response.description()).isEqualTo("New Description");
        
        ArgumentCaptor<String> detailsCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).logAction(
                eq(actorId),
                eq("admin"),
                eq(AuditEventAction.CLASS_UPDATED),
                eq("CLASS"),
                eq(existingClass.getId().toString()),
                detailsCaptor.capture(),
                eq(null),
                any());
                
        assertThat(detailsCaptor.getValue()).contains("Old Name -> New Name");
    }

    @Test
    void updateClass_ClassNotFound() {
        UpdateClassRequest request = new UpdateClassRequest("New Name", null, null, null, 0L);
        when(studyClassRepository.findById(classId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> classUpdateService.updateClass(classId, request, actorJwt))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CLASS_NOT_FOUND);
    }

    @Test
    void updateClass_ConcurrentModification() {
        UpdateClassRequest request = new UpdateClassRequest("New Name", null, null, null, 1L); // version mismatch
        when(studyClassRepository.findById(classId)).thenReturn(Optional.of(existingClass)); // version is 0

        assertThatThrownBy(() -> classUpdateService.updateClass(classId, request, actorJwt))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONCURRENT_MODIFICATION);
    }

    @Test
    void updateClass_InvalidDates() {
        Instant now = Instant.now();
        Instant openedAt = now.plus(2, ChronoUnit.DAYS);
        Instant closedAt = now.plus(1, ChronoUnit.DAYS);
        
        UpdateClassRequest request = new UpdateClassRequest("New Name", null, openedAt, closedAt, 0L);
        when(studyClassRepository.findById(classId)).thenReturn(Optional.of(existingClass));

        assertThatThrownBy(() -> classUpdateService.updateClass(classId, request, actorJwt))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CLASS_DATES);
    }
}
