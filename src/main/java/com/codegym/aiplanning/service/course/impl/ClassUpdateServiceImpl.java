package com.codegym.aiplanning.service.course.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.config.RequestIdFilter;
import com.codegym.aiplanning.controller.course.dto.ClassResponse;
import com.codegym.aiplanning.controller.course.dto.UpdateClassRequest;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.course.StudyClass;
import com.codegym.aiplanning.repository.course.StudyClassRepository;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.service.course.ClassUpdateService;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassUpdateServiceImpl implements ClassUpdateService {

    private static final String AUDIT_RESOURCE = "CLASS";

    private final StudyClassRepository studyClassRepository;
    private final AuditLogService auditLogService;

    public ClassUpdateServiceImpl(StudyClassRepository studyClassRepository, AuditLogService auditLogService) {
        this.studyClassRepository = studyClassRepository;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    public ClassResponse updateClass(UUID classId, UpdateClassRequest request, Jwt actorJwt) {
        Actor actor = actor(actorJwt);
        StudyClass studyClass = studyClassRepository
                .findById(classId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.CLASS_NOT_FOUND, "Class not found with id: " + classId));

        if (studyClass.getVersion() != request.version()) {
            throw new BusinessException(
                    ErrorCode.CONCURRENT_MODIFICATION,
                    "Data has been updated by another user. Please refresh and try again.");
        }

        String oldName = studyClass.getName();
        String oldDescription = studyClass.getDescription();
        String oldOpenedAt = studyClass.getOpenedAt() != null ? studyClass.getOpenedAt().toString() : "null";
        String oldClosedAt = studyClass.getClosedAt() != null ? studyClass.getClosedAt().toString() : "null";

        studyClass.updateDetails(
                request.name(), request.description(), request.openedAt(), request.closedAt(), actor.id());

        String newName = studyClass.getName();
        String newDescription = studyClass.getDescription();
        String newOpenedAt = studyClass.getOpenedAt() != null ? studyClass.getOpenedAt().toString() : "null";
        String newClosedAt = studyClass.getClosedAt() != null ? studyClass.getClosedAt().toString() : "null";

        String details = String.format(
                "Updated class '%s'. Changes: name: [%s -> %s], description: [%s -> %s], openedAt: [%s -> %s], closedAt: [%s -> %s]",
                studyClass.getCode(), oldName, newName, oldDescription, newDescription, oldOpenedAt, newOpenedAt, oldClosedAt, newClosedAt);

        audit(actor, AuditEventAction.CLASS_UPDATED, studyClass, details);

        return ClassResponse.from(studyClass);
    }

    private Actor actor(Jwt jwt) {
        if (jwt == null) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED, "Authentication is required.");
        }
        if (jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED, "Authentication is invalid.");
        }
        try {
            UUID actorId = UUID.fromString(jwt.getSubject());
            String username = jwt.getClaimAsString("preferred_username");
            return new Actor(actorId, username == null || username.isBlank() ? jwt.getSubject() : username);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED, "Authentication is invalid.");
        }
    }

    private void audit(Actor actor, AuditEventAction action, StudyClass studyClass, String details) {
        auditLogService.logAction(
                actor.id(),
                actor.username(),
                action,
                AUDIT_RESOURCE,
                studyClass.getId().toString(),
                details,
                null,
                MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY));
    }

    private record Actor(UUID id, String username) {}
}
