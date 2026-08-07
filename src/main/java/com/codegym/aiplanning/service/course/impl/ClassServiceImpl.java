package com.codegym.aiplanning.service.course.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.admin.dto.course.ClassResponse;
import com.codegym.aiplanning.controller.admin.dto.course.CreateClassRequest;
import com.codegym.aiplanning.entity.course.StudyClass;
import com.codegym.aiplanning.repository.course.CourseRepository;
import com.codegym.aiplanning.repository.course.StudyClassRepository;
import com.codegym.aiplanning.service.course.ClassService;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.controller.admin.dto.course.UpdateClassRequest;
import com.codegym.aiplanning.controller.admin.dto.course.ChangeClassStatusRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassServiceImpl implements ClassService {

    private final StudyClassRepository studyClassRepository;
    private final CourseRepository courseRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public ClassServiceImpl(StudyClassRepository studyClassRepository, 
                            CourseRepository courseRepository,
                            AuditLogService auditLogService,
                            ObjectMapper objectMapper) {
        this.studyClassRepository = studyClassRepository;
        this.courseRepository = courseRepository;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public ClassResponse createClass(CreateClassRequest request) {
        if (!courseRepository.existsById(request.courseId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Course not found");
        }

        if (studyClassRepository.existsByCodeIgnoreCase(request.code())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Class code already exists");
        }

        StudyClass studyClass = StudyClass.create(
                request.courseId(),
                request.code(),
                request.name(),
                request.description(),
                request.status()
        );

        studyClass = studyClassRepository.save(studyClass);
        return ClassResponse.from(studyClass);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ClassResponse> getAllClasses(Pageable pageable) {
        return studyClassRepository.findAll(pageable).map(ClassResponse::from);
    }

    @Override
    @Transactional
    public ClassResponse updateClass(UUID id, UpdateClassRequest request) {
        if (request.openedAt() != null && request.closedAt() != null && request.openedAt().isAfter(request.closedAt())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Ngày mở lớp không được sau ngày đóng lớp");
        }

        StudyClass studyClass = studyClassRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Class not found"));

        try {
            String beforeState = objectMapper.writeValueAsString(ClassResponse.from(studyClass));
            
            studyClass.updateInfo(request.name(), request.description(), request.openedAt(), request.closedAt());
            studyClass.setVersion(request.version()); // Trigger optimistic locking

            StudyClass updatedClass = studyClassRepository.saveAndFlush(studyClass);
            
            String afterState = objectMapper.writeValueAsString(ClassResponse.from(updatedClass));
            String details = String.format("{\"before\": %s, \"after\": %s}", beforeState, afterState);
            auditLogService.logAction(null, "system", AuditEventAction.UPDATE_CLASS, "classes", id.toString(), details);
            
            return ClassResponse.from(updatedClass);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException | BusinessException ex) {
            throw ex;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to update class: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ClassResponse changeStatus(UUID id, ChangeClassStatusRequest request) {
        StudyClass studyClass = studyClassRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Class not found"));

        try {
            String beforeState = objectMapper.writeValueAsString(ClassResponse.from(studyClass));
            
            // Domain logic controls the state machine, throws IllegalStateException on failure
            studyClass.changeStatus(request.status());
            studyClass.setVersion(request.version());

            StudyClass updatedClass = studyClassRepository.saveAndFlush(studyClass);
            
            String afterState = objectMapper.writeValueAsString(ClassResponse.from(updatedClass));
            String details = String.format("{\"before\": %s, \"after\": %s}", beforeState, afterState);
            auditLogService.logAction(null, "system", AuditEventAction.CHANGE_CLASS_STATUS, "classes", id.toString(), details);
            
            return ClassResponse.from(updatedClass);
        } catch (IllegalStateException e) {
            // Map Domain exception to Application exception
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, e.getMessage());
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException | BusinessException ex) {
            throw ex;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to change class status: " + e.getMessage());
        }
    }
}
