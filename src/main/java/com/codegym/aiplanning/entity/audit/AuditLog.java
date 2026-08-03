package com.codegym.aiplanning.entity.audit;

import com.codegym.aiplanning.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLog extends BaseEntity {

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_username", nullable = false, length = 100)
    private String actorUsername;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AuditEventAction action;

    @Column(name = "target_resource", nullable = false, length = 50)
    private String targetResource;

    @Column(name = "target_id", length = 100)
    private String targetId;

    @Column(columnDefinition = "TEXT")
    private String details;

    protected AuditLog() {}

    public static AuditLog create(
            UUID actorId,
            String actorUsername,
            AuditEventAction action,
            String targetResource,
            String targetId,
            String details) {
        AuditLog log = new AuditLog();
        log.actorId = actorId;
        log.actorUsername = actorUsername;
        log.action = action;
        log.targetResource = targetResource;
        log.targetId = targetId;
        log.details = details;
        return log;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public AuditEventAction getAction() {
        return action;
    }

    public String getTargetResource() {
        return targetResource;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getDetails() {
        return details;
    }
}
