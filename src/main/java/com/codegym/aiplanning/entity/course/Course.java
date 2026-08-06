package com.codegym.aiplanning.entity.course;

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
        name = "courses",
        uniqueConstraints = @UniqueConstraint(name = "uk_courses_code", columnNames = "code"))
public class Course extends BaseEntity {

    @Column(nullable = false, length = 50, updatable = false)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CourseStatus status;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    protected Course() {}

    public static Course create(
            String code, String name, String description, UUID actorId) {
        Course course = new Course();
        course.code = code;
        course.name = name;
        course.description = description;
        course.status = CourseStatus.ACTIVE;
        course.createdBy = actorId;
        course.updatedBy = actorId;
        return course;
    }

    public void updateDetails(String name, String description, UUID actorId) {
        this.name = name;
        this.description = description;
        this.updatedBy = actorId;
    }

    public boolean activate(UUID actorId) {
        if (status == CourseStatus.ACTIVE) {
            return false;
        }
        status = CourseStatus.ACTIVE;
        updatedBy = actorId;
        return true;
    }

    public boolean deactivate(UUID actorId) {
        if (status == CourseStatus.INACTIVE) {
            return false;
        }
        status = CourseStatus.INACTIVE;
        updatedBy = actorId;
        return true;
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

    public CourseStatus getStatus() {
        return status;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }
}
