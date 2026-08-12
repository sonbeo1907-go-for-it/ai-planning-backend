package com.codegym.aiplanning.entity.audit;

import com.codegym.aiplanning.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_logs")
public class AuditLog extends BaseEntity {

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_email", nullable = false, length = 254)
    private String actorEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AuditEventAction action;

    @Column(name = "target_resource", nullable = false, length = 50)
    private String targetResource;

    @Column(name = "target_id", length = 100)
    private String targetId;

    @Column(columnDefinition = "TEXT")
    private String details;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "request_id", length = 100)
    private String requestId;

    protected AuditLog() {}

    public static AuditLog create(
            UUID actorId,
            String actorEmail,
            AuditEventAction action,
            String targetResource,
            String targetId,
            String requestId) {
        AuditLog log = new AuditLog();
        log.actorId = actorId;
        log.actorEmail = actorEmail;
        log.action = action;
        log.targetResource = targetResource;
        log.targetId = targetId;
        log.requestId = requestId;
        return log;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getActorEmail() {
        return actorEmail;
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

    public String getMetadata() {
        return metadata;
    }

    public String getRequestId() {
        return requestId;
    }
}
