package com.codegym.aiplanning.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.audit.AuditLog;
import com.codegym.aiplanning.repository.audit.AuditLogRepository;
import com.codegym.aiplanning.service.audit.impl.AuditLogServiceImpl;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditLogServiceImpl auditLogService;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogServiceImpl(auditLogRepository);
    }

    @Test
    void logAction_withBasicDetails_savesAuditLog() {
        UUID actorId = UUID.randomUUID();
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        auditLogService.logAction(
                actorId,
                "admin@example.com",
                AuditEventAction.USER_DISABLED,
                "USER",
                "user-123",
                "Disabled user"
        );

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getActorId()).isEqualTo(actorId);
        assertThat(saved.getActorUsername()).isEqualTo("admin@example.com");
        assertThat(saved.getAction()).isEqualTo(AuditEventAction.USER_DISABLED);
        assertThat(saved.getTargetResource()).isEqualTo("USER");
        assertThat(saved.getTargetId()).isEqualTo("user-123");
        assertThat(saved.getDetails()).isEqualTo("Disabled user");
        assertThat(saved.getMetadata()).isNull();
        assertThat(saved.getRequestId()).isNull();
    }

    @Test
    void logAction_withMetadataAndRequestId_savesEnrichedAuditLog() {
        UUID actorId = UUID.randomUUID();
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String metadataJson = "{\"studentId\":\"student-uuid\",\"classId\":\"class-uuid\"}";
        String requestId = "req-9999";

        auditLogService.logAction(
                actorId,
                "instructor@example.com",
                AuditEventAction.STUDENT_ADDED_TO_CLASS,
                "CLASS_MEMBERSHIP",
                "membership-1",
                "Added student to class",
                metadataJson,
                requestId
        );

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getActorId()).isEqualTo(actorId);
        assertThat(saved.getActorUsername()).isEqualTo("instructor@example.com");
        assertThat(saved.getAction()).isEqualTo(AuditEventAction.STUDENT_ADDED_TO_CLASS);
        assertThat(saved.getTargetResource()).isEqualTo("CLASS_MEMBERSHIP");
        assertThat(saved.getTargetId()).isEqualTo("membership-1");
        assertThat(saved.getMetadata()).isEqualTo(metadataJson);
        assertThat(saved.getRequestId()).isEqualTo(requestId);
    }
}
