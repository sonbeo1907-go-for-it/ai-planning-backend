package com.codegym.aiplanning.service.course.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.config.RequestIdFilter;
import com.codegym.aiplanning.controller.course.dto.ClassResponse;
import com.codegym.aiplanning.controller.course.dto.CreateClassRequest;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.course.StudyClass;
import com.codegym.aiplanning.repository.course.StudyClassRepository;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.service.course.ClassCreationService;
import com.codegym.aiplanning.service.course.StudyClassValidator;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassCreationServiceImpl implements ClassCreationService {

    private static final String AUDIT_RESOURCE = "CLASS";

    private final StudyClassValidator validator;
    private final StudyClassRepository studyClassRepository;
    private final AuditLogService auditLogService;

    public ClassCreationServiceImpl(
            StudyClassValidator validator,
            StudyClassRepository studyClassRepository,
            AuditLogService auditLogService) {
        this.validator = validator;
        this.studyClassRepository = studyClassRepository;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    public ClassResponse createClass(CreateClassRequest request, Jwt actorJwt) {
        Actor actor = actor(actorJwt);

        validator.validateCourseExists(request.courseId());
        validator.validateDuplicateCode(request.code());

        StudyClass studyClass = StudyClass.create(
                request.courseId(), request.code(), request.name(), request.description(), actor.id());

        StudyClass saved;
        try {
            saved = studyClassRepository.saveAndFlush(studyClass);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(
                    ErrorCode.CLASS_CODE_ALREADY_EXISTS,
                    "A class with code '" + studyClass.getCode() + "' already exists.");
        }

        audit(
                actor,
                AuditEventAction.CLASS_CREATED,
                saved,
                "Created class '" + saved.getCode() + "'");

        return ClassResponse.from(saved);
    }

    private Actor actor(Jwt jwt) {
        if (jwt == null) {
            throw new BusinessException(
                    ErrorCode.AUTHENTICATION_REQUIRED, "Authentication is required.");
        }
        if (jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new BusinessException(
                    ErrorCode.AUTHENTICATION_REQUIRED, "Authentication is invalid.");
        }
        try {
            UUID actorId = UUID.fromString(jwt.getSubject());
            String username = jwt.getClaimAsString("preferred_username");
            return new Actor(
                    actorId,
                    username == null || username.isBlank() ? jwt.getSubject() : username);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.AUTHENTICATION_REQUIRED, "Authentication is invalid.");
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
