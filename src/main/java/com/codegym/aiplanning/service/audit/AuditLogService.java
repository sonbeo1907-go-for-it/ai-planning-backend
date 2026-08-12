package com.codegym.aiplanning.service.audit;

import com.codegym.aiplanning.entity.audit.AuditEventAction;
import java.util.UUID;

public interface AuditLogService {

    /**
     * Records an operational event using identifiers only. Free-form details and metadata are
     * intentionally excluded so prompts, uploaded content, provider secrets, and other personal
     * learning content cannot be copied into the audit trail.
     */
    void logAction(
            UUID actorId,
            String actorUsername,
            AuditEventAction action,
            String targetResource,
            String targetId);
}
