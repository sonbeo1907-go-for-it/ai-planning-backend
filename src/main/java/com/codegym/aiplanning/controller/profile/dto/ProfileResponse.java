package com.codegym.aiplanning.controller.profile.dto;

import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.service.profile.model.ProfileRoleDetails;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProfileResponse(
        UUID id,
        String username,
        String email,
        String fullName,
        UserRole role,
        AccountStatus status,
        StudentProfile student,
        InstructorProfile instructor) {

    public static ProfileResponse from(UserAccount account, ProfileRoleDetails details) {
        return new ProfileResponse(
                account.getId(),
                account.getUsername(),
                account.getEmail(),
                account.getFullName(),
                account.getRole(),
                account.getStatus(),
                details.student(),
                details.instructor());
    }

    public record StudentProfile(List<EnrollmentSummary> currentEnrollments) {}

    public record EnrollmentSummary(
            UUID enrollmentId,
            UUID classId,
            String classCode,
            String className,
            UUID courseId,
            String courseCode,
            String courseName) {}

    public record InstructorProfile(List<AssignedClassSummary> assignedClasses) {}

    public record AssignedClassSummary(
            UUID classId,
            String classCode,
            String className,
            UUID courseId,
            String courseCode,
            String courseName) {}
}
