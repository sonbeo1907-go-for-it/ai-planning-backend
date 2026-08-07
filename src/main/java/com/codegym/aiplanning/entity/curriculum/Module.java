package com.codegym.aiplanning.entity.curriculum;

import com.codegym.aiplanning.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

@Entity
@Table(
        name = "modules",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_modules_course_code", columnNames = {"course_id", "code"}),
            @UniqueConstraint(name = "uk_modules_course_sequence", columnNames = {"course_id", "sequence_number"})
        })
public class Module extends BaseEntity {

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    @Column(nullable = false, length = 50, updatable = false)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModuleStatus status;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    protected Module() {}

    public static Module create(
            UUID courseId,
            String code,
            String name,
            String description,
            Integer sequenceNumber,
            UUID actorId) {
        Module module = new Module();
        module.courseId = courseId;
        module.code = code;
        module.name = name;
        module.description = description;
        module.sequenceNumber = sequenceNumber;
        module.status = ModuleStatus.ACTIVE;
        module.createdBy = actorId;
        module.updatedBy = actorId;
        return module;
    }

    public void updateDetails(String name, String description, UUID actorId) {
        this.name = name;
        this.description = description;
        this.updatedBy = actorId;
    }

    public void updateSequenceNumber(Integer newSequenceNumber, UUID actorId) {
        this.sequenceNumber = newSequenceNumber;
        this.updatedBy = actorId;
    }

    public boolean activate(UUID actorId) {
        if (status == ModuleStatus.ACTIVE) {
            return false;
        }
        status = ModuleStatus.ACTIVE;
        updatedBy = actorId;
        return true;
    }

    public boolean deactivate(UUID actorId) {
        if (status == ModuleStatus.INACTIVE) {
            return false;
        }
        status = ModuleStatus.INACTIVE;
        updatedBy = actorId;
        return true;
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

    public Integer getSequenceNumber() {
        return sequenceNumber;
    }

    public ModuleStatus getStatus() {
        return status;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }
}
