package com.codegym.aiplanning.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.config.RequestIdFilter;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.audit.AuditLog;
import com.codegym.aiplanning.repository.audit.AuditLogRepository;
import com.codegym.aiplanning.service.audit.impl.AuditLogServiceImpl;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditLogServiceImpl auditLogService;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogServiceImpl(auditLogRepository);
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void logActionStoresOnlyIdentifiersAndRequestCorrelation() {
        UUID actorId = UUID.randomUUID();
        when(auditLogRepository.save(any(AuditLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        MDC.put(RequestIdFilter.REQUEST_ID_MDC_KEY, "req-9999");

        auditLogService.logAction(
                actorId,
                "user@example.com",
                AuditEventAction.PROFILE_UPDATED,
                "UserProfile",
                "profile-123");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getActorId()).isEqualTo(actorId);
        assertThat(saved.getActorEmail()).isEqualTo("user@example.com");
        assertThat(saved.getAction()).isEqualTo(AuditEventAction.PROFILE_UPDATED);
        assertThat(saved.getTargetResource()).isEqualTo("UserProfile");
        assertThat(saved.getTargetId()).isEqualTo("profile-123");
        assertThat(saved.getDetails()).isNull();
        assertThat(saved.getMetadata()).isNull();
        assertThat(saved.getRequestId()).isEqualTo("req-9999");
    }
}
