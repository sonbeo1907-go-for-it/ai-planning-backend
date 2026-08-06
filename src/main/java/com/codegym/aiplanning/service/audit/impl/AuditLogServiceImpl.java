package com.codegym.aiplanning.service.audit.impl;

import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.audit.AuditLog;
import com.codegym.aiplanning.repository.audit.AuditLogRepository;
import com.codegym.aiplanning.service.audit.AuditLogService;
import java.util.UUID;
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
            String targetId,
            String details) {
        logAction(actorId, actorUsername, action, targetResource, targetId, details, null, null);
    }

    @Override
    @Transactional
    public void logAction(
            UUID actorId,
            String actorUsername,
            AuditEventAction action,
            String targetResource,
            String targetId,
            String details,
            String metadata,
            String requestId) {
        AuditLog auditLog = AuditLog.create(
                actorId, actorUsername, action, targetResource, targetId, details, metadata, requestId);
        auditLogRepository.save(auditLog);
    }
}
