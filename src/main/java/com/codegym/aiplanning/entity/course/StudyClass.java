package com.codegym.aiplanning.entity.course;

import com.codegym.aiplanning.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(
        name = "classes",
        uniqueConstraints = @UniqueConstraint(name = "uk_classes_code", columnNames = "code"))
public class StudyClass extends BaseEntity {

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    @Column(nullable = false, length = 50, updatable = false)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClassStatus status;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    protected StudyClass() {}

    public static StudyClass create(
            UUID courseId, String code, String name, String description, UUID actorId) {
        StudyClass studyClass = new StudyClass();
        studyClass.courseId = courseId;
        studyClass.code = code.trim().toUpperCase(Locale.ROOT);
        studyClass.name = name.trim();
        studyClass.description = (description == null || description.isBlank()) ? null : description.trim();
        studyClass.status = ClassStatus.PLANNED;
        studyClass.createdBy = actorId;
        studyClass.updatedBy = actorId;
        return studyClass;
    }

    public void updateDetails(
            String name, String description, Instant openedAt, Instant closedAt, UUID actorId) {
        validateDates(openedAt, closedAt);
        this.name = name.trim();
        this.description = (description == null || description.isBlank()) ? null : description.trim();
        this.openedAt = openedAt;
        this.closedAt = closedAt;
        this.updatedBy = actorId;
    }

    public void changeStatus(ClassStatus newStatus, UUID actorId) {
        if (this.status == newStatus) {
            return;
        }

        // Validate state transitions
        if (this.status == ClassStatus.PLANNED && newStatus != ClassStatus.ACTIVE) {
            throw new com.codegym.aiplanning.common.exception.BusinessException(
                    com.codegym.aiplanning.common.exception.ErrorCode.INVALID_STATUS_TRANSITION,
                    "From PLANNED, class can only transition to ACTIVE.");
        }
        if (this.status == ClassStatus.ACTIVE && newStatus != ClassStatus.CLOSED) {
            throw new com.codegym.aiplanning.common.exception.BusinessException(
                    com.codegym.aiplanning.common.exception.ErrorCode.INVALID_STATUS_TRANSITION,
                    "From ACTIVE, class can only transition to CLOSED.");
        }
        if (this.status == ClassStatus.CLOSED) {
            throw new com.codegym.aiplanning.common.exception.BusinessException(
                    com.codegym.aiplanning.common.exception.ErrorCode.INVALID_STATUS_TRANSITION,
                    "CLOSED class cannot change status.");
        }

        // Assign dates if transitioning to active/closed and current date is null
        if (newStatus == ClassStatus.ACTIVE && this.openedAt == null) {
            this.openedAt = Instant.now();
        } else if (newStatus == ClassStatus.CLOSED && this.closedAt == null) {
            this.closedAt = Instant.now();
        }

        this.status = newStatus;
        this.updatedBy = actorId;
    }

    private void validateDates(Instant openedAt, Instant closedAt) {
        if (openedAt != null && closedAt != null) {
            if (openedAt.isAfter(closedAt)) {
                throw new com.codegym.aiplanning.common.exception.BusinessException(
                        com.codegym.aiplanning.common.exception.ErrorCode.INVALID_CLASS_DATES,
                        "Opened date must not be after closed date.");
            }
        }
    }

    public UUID getCourseId() {
        return courseId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ClassStatus getStatus() {
        return status;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }
}
