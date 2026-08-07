package com.codegym.aiplanning.entity.course;

import com.codegym.aiplanning.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "classes")
public class StudyClass extends BaseEntity {

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ClassStatus status;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected StudyClass() {}

    public static StudyClass create(UUID courseId, String code, String name, String description, ClassStatus status) {
        StudyClass studyClass = new StudyClass();
        studyClass.courseId = courseId;
        studyClass.code = code.trim();
        studyClass.name = name.trim();
        studyClass.description = description;
        studyClass.status = status;
        
        if (status == ClassStatus.ACTIVE) {
            studyClass.openedAt = Instant.now();
        } else if (status == ClassStatus.CLOSED) {
            studyClass.closedAt = Instant.now();
        }
        return studyClass;
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

    public void updateInfo(String name, String description, Instant openedAt, Instant closedAt) {
        if (name != null && !name.isBlank()) {
            this.name = name.trim();
        }
        this.description = description;
        this.openedAt = openedAt;
        this.closedAt = closedAt;
    }

    public void changeStatus(ClassStatus newStatus) {
        if (newStatus == null) {
            throw new IllegalArgumentException("Trạng thái mới không được để trống.");
        }

        if (this.status == newStatus) {
            throw new IllegalStateException("Lớp học đã ở trạng thái " + newStatus + ".");
        }

        if (this.status == ClassStatus.PLANNED && newStatus != ClassStatus.ACTIVE) {
            throw new IllegalStateException("Lớp học đang dự kiến chỉ có thể chuyển sang trạng thái Đang hoạt động.");
        }

        if (this.status == ClassStatus.ACTIVE && newStatus != ClassStatus.CLOSED) {
            throw new IllegalStateException("Lớp học đang hoạt động chỉ có thể chuyển sang trạng thái Đã đóng.");
        }

        if (this.status == ClassStatus.CLOSED) {
            throw new IllegalStateException("Lớp học đã đóng không thể thay đổi trạng thái.");
        }

        this.status = newStatus;
        if (newStatus == ClassStatus.ACTIVE && this.openedAt == null) {
            this.openedAt = Instant.now();
        } else if (newStatus == ClassStatus.CLOSED && this.closedAt == null) {
            this.closedAt = Instant.now();
        }
    }
}
