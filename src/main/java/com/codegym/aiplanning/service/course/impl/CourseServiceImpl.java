package com.codegym.aiplanning.service.course.impl;

import com.codegym.aiplanning.common.api.PageResponse;
import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.config.RequestIdFilter;
import com.codegym.aiplanning.controller.course.dto.CourseResponse;
import com.codegym.aiplanning.controller.course.dto.CourseSearchParam;
import com.codegym.aiplanning.controller.course.dto.CreateCourseRequest;
import com.codegym.aiplanning.controller.course.dto.UpdateCourseRequest;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.course.Course;
import com.codegym.aiplanning.repository.course.CourseRepository;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.service.course.CourseService;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseServiceImpl implements CourseService {

    private static final String AUDIT_RESOURCE = "COURSE";

    private final CourseRepository courseRepository;
    private final AuditLogService auditLogService;

    public CourseServiceImpl(
            CourseRepository courseRepository, AuditLogService auditLogService) {
        this.courseRepository = courseRepository;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    public CourseResponse createCourse(CreateCourseRequest request, Jwt actorJwt) {
        Actor actor = actor(actorJwt);
        String code = normalizeCode(request.code());
        if (courseRepository.existsByCodeIgnoreCase(code)) {
            throw courseCodeAlreadyExists(code);
        }

        Course course = Course.create(
                code,
                request.name().trim(),
                normalizeDescription(request.description()),
                actor.id());
        Course saved;
        try {
            saved = courseRepository.saveAndFlush(course);
        } catch (DataIntegrityViolationException exception) {
            throw courseCodeAlreadyExists(code);
        }

        audit(
                actor,
                AuditEventAction.COURSE_CREATED,
                saved,
                "Created course '" + saved.getCode() + "'");
        return CourseResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CourseResponse> getCourses(CourseSearchParam param) {
        Pageable pageable = PageRequest.of(
                param.resolvedPage(),
                param.resolvedSize(),
                Sort.by(Sort.Direction.ASC, "code"));

        Specification<Course> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (param.search() != null && !param.search().isBlank()) {
                String search = "%" + param.search().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("code")), search),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), search)));
            }
            if (param.status() != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), param.status()));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        Page<CourseResponse> page = courseRepository
                .findAll(specification, pageable)
                .map(CourseResponse::from);
        return PageResponse.from(page);
    }

    @Override
    @Transactional(readOnly = true)
    public CourseResponse getCourse(UUID courseId) {
        return CourseResponse.from(findCourse(courseId));
    }

    @Override
    @Transactional
    public CourseResponse updateCourse(
            UUID courseId, UpdateCourseRequest request, Jwt actorJwt) {
        Actor actor = actor(actorJwt);
        Course course = findCourse(courseId);
        course.updateDetails(
                request.name().trim(),
                normalizeDescription(request.description()),
                actor.id());
        Course updated = courseRepository.save(course);

        audit(
                actor,
                AuditEventAction.COURSE_UPDATED,
                updated,
                "Updated course '" + updated.getCode() + "'");
        return CourseResponse.from(updated);
    }

    @Override
    @Transactional
    public CourseResponse deactivateCourse(UUID courseId, Jwt actorJwt) {
        Actor actor = actor(actorJwt);
        Course course = findCourse(courseId);
        if (course.deactivate(actor.id())) {
            courseRepository.save(course);
            audit(
                    actor,
                    AuditEventAction.COURSE_DEACTIVATED,
                    course,
                    "Deactivated course '" + course.getCode() + "'");
        }
        return CourseResponse.from(course);
    }

    @Override
    @Transactional
    public CourseResponse activateCourse(UUID courseId, Jwt actorJwt) {
        Actor actor = actor(actorJwt);
        Course course = findCourse(courseId);
        if (course.activate(actor.id())) {
            courseRepository.save(course);
            audit(
                    actor,
                    AuditEventAction.COURSE_ACTIVATED,
                    course,
                    "Activated course '" + course.getCode() + "'");
        }
        return CourseResponse.from(course);
    }

    private Course findCourse(UUID courseId) {
        return courseRepository
                .findById(courseId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.COURSE_NOT_FOUND,
                        "Course not found with id: " + courseId));
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
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

    private BusinessException courseCodeAlreadyExists(String code) {
        return new BusinessException(
                ErrorCode.COURSE_CODE_ALREADY_EXISTS,
                "A course with code '" + code + "' already exists.");
    }

    private void audit(
            Actor actor, AuditEventAction action, Course course, String details) {
        auditLogService.logAction(
                actor.id(),
                actor.username(),
                action,
                AUDIT_RESOURCE,
                course.getId().toString(),
                details,
                null,
                MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY));
    }

    private record Actor(UUID id, String username) {}
}
