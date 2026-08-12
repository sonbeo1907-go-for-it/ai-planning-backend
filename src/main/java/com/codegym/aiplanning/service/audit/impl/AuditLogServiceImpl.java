package com.codegym.aiplanning.service.audit.impl;

import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.audit.AuditLog;
import com.codegym.aiplanning.config.RequestIdFilter;
import com.codegym.aiplanning.repository.audit.AuditLogRepository;
import com.codegym.aiplanning.service.audit.AuditLogService;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogServiceImpl(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    @Transactional
    public void logAction(
            UUID actorId,
            String actorUsername,
            AuditEventAction action,
            String targetResource,
            String targetId) {
        AuditLog auditLog = AuditLog.create(
                actorId,
                actorUsername,
                action,
                targetResource,
                targetId,
                MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY));
        auditLogRepository.save(auditLog);
    }
}
