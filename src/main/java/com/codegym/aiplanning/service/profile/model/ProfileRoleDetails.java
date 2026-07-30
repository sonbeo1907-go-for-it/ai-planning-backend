package com.codegym.aiplanning.service.profile.model;

import com.codegym.aiplanning.controller.profile.dto.ProfileResponse.InstructorProfile;
import com.codegym.aiplanning.controller.profile.dto.ProfileResponse.StudentProfile;
import com.codegym.aiplanning.entity.auth.UserRole;
import java.util.List;

public record ProfileRoleDetails(StudentProfile student, InstructorProfile instructor) {

    public static ProfileRoleDetails emptyFor(UserRole role) {
        return switch (role) {
            case STUDENT -> new ProfileRoleDetails(new StudentProfile(List.of()), null);
            case INSTRUCTOR -> new ProfileRoleDetails(null, new InstructorProfile(List.of()));
            case ADMIN -> new ProfileRoleDetails(null, null);
        };
    }
}
