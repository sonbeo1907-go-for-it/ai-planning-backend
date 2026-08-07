package com.codegym.aiplanning.service.curriculum.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.config.RequestIdFilter;
import com.codegym.aiplanning.controller.curriculum.dto.CreateModuleRequest;
import com.codegym.aiplanning.controller.curriculum.dto.ModuleOrderItem;
import com.codegym.aiplanning.controller.curriculum.dto.ModuleResponse;
import com.codegym.aiplanning.controller.curriculum.dto.ReorderModulesRequest;
import com.codegym.aiplanning.controller.curriculum.dto.UpdateModuleRequest;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.course.Course;
import com.codegym.aiplanning.entity.curriculum.Module;
import com.codegym.aiplanning.repository.course.CourseRepository;
import com.codegym.aiplanning.repository.curriculum.ModuleRepository;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.service.curriculum.ModuleService;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModuleServiceImpl implements ModuleService {

    private static final String AUDIT_RESOURCE = "MODULE";

    private final ModuleRepository moduleRepository;
    private final CourseRepository courseRepository;
    private final AuditLogService auditLogService;

    public ModuleServiceImpl(
            ModuleRepository moduleRepository,
            CourseRepository courseRepository,
            AuditLogService auditLogService) {
        this.moduleRepository = moduleRepository;
        this.courseRepository = courseRepository;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    public ModuleResponse createModule(UUID courseId, CreateModuleRequest request, Jwt actorJwt) {
        Actor actor = actor(actorJwt);

        Course course = courseRepository
                .findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND, "Course not found: " + courseId));

        String code = normalizeCode(request.code());
        if (moduleRepository.existsByCourseIdAndCode(courseId, code)) {
            throw new BusinessException(
                    ErrorCode.MODULE_CODE_ALREADY_EXISTS,
                    "Module code already exists in this course: " + code);
        }

        Integer sequenceNumber = request.sequenceNumber();
        if (sequenceNumber == null) {
            sequenceNumber = moduleRepository.findMaxSequenceNumberByCourseId(courseId).orElse(0) + 1;
        } else if (moduleRepository.existsByCourseIdAndSequenceNumber(courseId, sequenceNumber)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "Sequence number " + sequenceNumber + " is already taken in this course");
        }

        Module module = Module.create(
                courseId,
                code,
                request.name().trim(),
                normalizeDescription(request.description()),
                sequenceNumber,
                actor.id());

        Module saved;
        try {
            saved = moduleRepository.save(module);
        } catch (DataIntegrityViolationException ex) {
            if (moduleRepository.existsByCourseIdAndCode(courseId, code)) {
                throw new BusinessException(
                        ErrorCode.MODULE_CODE_ALREADY_EXISTS,
                        "Module code already exists in this course: " + code);
            }
            throw ex;
        }

        String targetId = saved.getId() != null ? saved.getId().toString() : courseId.toString();

        auditLogService.logAction(
                actor.id(),
                actor.username(),
                AuditEventAction.MODULE_PROGRESS_CREATED,
                AUDIT_RESOURCE,
                targetId,
                "Created module: " + saved.getCode() + " in course: " + course.getCode(),
                String.format("{\"courseId\":\"%s\",\"code\":\"%s\",\"sequenceNumber\":%d}", courseId, code, sequenceNumber),
                currentRequestId());

        return ModuleResponse.from(saved);
    }

    @Override
    @Transactional
    public ModuleResponse updateModule(UUID courseId, UUID moduleId, UpdateModuleRequest request, Jwt actorJwt) {
        Actor actor = actor(actorJwt);

        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Module name is required.");
        }

        if (!courseRepository.existsById(courseId)) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND, "Course not found: " + courseId);
        }

        Module module = moduleRepository
                .findById(moduleId)
                .filter(m -> m.getCourseId().equals(courseId))
                .orElseThrow(() -> new BusinessException(ErrorCode.MODULE_NOT_FOUND, "Module not found: " + moduleId));

        module.updateDetails(request.name().trim(), normalizeDescription(request.description()), actor.id());
        Module saved = moduleRepository.save(module);

        String targetId = saved.getId() != null ? saved.getId().toString() : moduleId.toString();
        String safeName = saved.getName() != null ? saved.getName().replace("\"", "\\\"") : "";

        auditLogService.logAction(
                actor.id(),
                actor.username(),
                AuditEventAction.MODULE_PROGRESS_UPDATED,
                AUDIT_RESOURCE,
                targetId,
                "Updated module: " + saved.getCode(),
                String.format("{\"courseId\":\"%s\",\"name\":\"%s\"}", courseId, safeName),
                currentRequestId());

        return ModuleResponse.from(saved);
    }

    @Override
    @Transactional
    public List<ModuleResponse> reorderModules(UUID courseId, ReorderModulesRequest request, Jwt actorJwt) {
        Actor actor = actor(actorJwt);

        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Items list cannot be empty.");
        }

        if (!courseRepository.existsById(courseId)) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND, "Course not found: " + courseId);
        }

        Set<UUID> moduleIdsInReq = new HashSet<>();
        Set<Integer> seqsInReq = new HashSet<>();
        for (ModuleOrderItem item : request.items()) {
            if (!moduleIdsInReq.add(item.moduleId())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Duplicate moduleId in reorder request: " + item.moduleId());
            }
            if (!seqsInReq.add(item.sequenceNumber())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Duplicate sequenceNumber in reorder request: " + item.sequenceNumber());
            }
        }

        Map<UUID, Module> existingMap = moduleRepository.findByCourseIdOrderBySequenceNumberAsc(courseId)
                .stream()
                .collect(Collectors.toMap(Module::getId, m -> m));

        for (ModuleOrderItem item : request.items()) {
            if (!existingMap.containsKey(item.moduleId())) {
                throw new BusinessException(ErrorCode.MODULE_NOT_FOUND, "Module not found in this course: " + item.moduleId());
            }
        }

        // Phase 1: Set temporary positive offset sequence numbers (> 0) to avoid UNIQUE constraint and CHECK (> 0) violations
        int offset = 1000000;
        int idx = 1;
        for (ModuleOrderItem item : request.items()) {
            Module m = existingMap.get(item.moduleId());
            m.updateSequenceNumber(offset + (idx++), actor.id());
        }
        moduleRepository.flush();

        // Phase 2: Set final target sequence numbers
        for (ModuleOrderItem item : request.items()) {
            Module m = existingMap.get(item.moduleId());
            m.updateSequenceNumber(item.sequenceNumber(), actor.id());
        }
        moduleRepository.flush();

        auditLogService.logAction(
                actor.id(),
                actor.username(),
                AuditEventAction.MODULE_PROGRESS_UPDATED,
                AUDIT_RESOURCE,
                courseId.toString(),
                "Reordered modules in course: " + courseId,
                String.format("{\"courseId\":\"%s\",\"itemCount\":%d}", courseId, request.items().size()),
                currentRequestId());

        return moduleRepository.findByCourseIdOrderBySequenceNumberAsc(courseId).stream()
                .map(ModuleResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public ModuleResponse activateModule(UUID courseId, UUID moduleId, Jwt actorJwt) {
        Actor actor = actor(actorJwt);

        if (!courseRepository.existsById(courseId)) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND, "Course not found: " + courseId);
        }

        Module module = moduleRepository
                .findById(moduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MODULE_NOT_FOUND, "Module not found: " + moduleId));

        if (!module.getCourseId().equals(courseId)) {
            throw new BusinessException(ErrorCode.MODULE_NOT_FOUND, "Module does not belong to course: " + courseId);
        }

        module.activate(actor.id());
        Module saved = moduleRepository.save(module);

        String targetId = saved.getId() != null ? saved.getId().toString() : moduleId.toString();

        auditLogService.logAction(
                actor.id(),
                actor.username(),
                AuditEventAction.MODULE_PROGRESS_UPDATED,
                AUDIT_RESOURCE,
                targetId,
                "Activated module: " + saved.getCode(),
                String.format("{\"courseId\":\"%s\",\"status\":\"ACTIVE\"}", courseId),
                currentRequestId());

        return ModuleResponse.from(saved);
    }

    @Override
    @Transactional
    public ModuleResponse deactivateModule(UUID courseId, UUID moduleId, Jwt actorJwt) {
        Actor actor = actor(actorJwt);

        if (!courseRepository.existsById(courseId)) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND, "Course not found: " + courseId);
        }

        Module module = moduleRepository
                .findById(moduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MODULE_NOT_FOUND, "Module not found: " + moduleId));

        if (!module.getCourseId().equals(courseId)) {
            throw new BusinessException(ErrorCode.MODULE_NOT_FOUND, "Module does not belong to course: " + courseId);
        }

        module.deactivate(actor.id());
        Module saved = moduleRepository.save(module);

        String deactTargetId = saved.getId() != null ? saved.getId().toString() : moduleId.toString();

        auditLogService.logAction(
                actor.id(),
                actor.username(),
                AuditEventAction.MODULE_PROGRESS_UPDATED,
                AUDIT_RESOURCE,
                deactTargetId,
                "Deactivated module: " + saved.getCode(),
                String.format("{\"courseId\":\"%s\",\"status\":\"INACTIVE\"}", courseId),
                currentRequestId());

        return ModuleResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ModuleResponse> getModulesByCourseId(UUID courseId) {
        if (!courseRepository.existsById(courseId)) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND, "Course not found: " + courseId);
        }
        return moduleRepository.findByCourseIdOrderBySequenceNumberAsc(courseId).stream()
                .map(ModuleResponse::from)
                .toList();
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String trimmed = description.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Actor actor(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED, "Authentication is invalid.");
        }
        try {
            UUID actorId = UUID.fromString(jwt.getSubject());
            String username = jwt.getClaimAsString("preferred_username");
            if (username == null || username.isBlank()) {
                username = jwt.getClaimAsString("email");
            }
            if (username == null || username.isBlank()) {
                username = jwt.getSubject();
            }
            return new Actor(actorId, username);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED, "Authentication is invalid.");
        }
    }

    private String currentRequestId() {
        return MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY);
    }

    private record Actor(UUID id, String username) {}
}
