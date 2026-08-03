package com.codegym.aiplanning.service.audit;

import com.codegym.aiplanning.entity.audit.AuditEventAction;
import java.util.UUID;

public interface AuditLogService {

    void logAction(
            UUID actorId,
            String actorUsername,
            AuditEventAction action,
            String targetResource,
            String targetId,
            String details);
}
