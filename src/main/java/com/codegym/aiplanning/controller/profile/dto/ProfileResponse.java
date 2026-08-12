package com.codegym.aiplanning.controller.profile.dto;

import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.entity.profile.UserProfile;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProfileResponse(
        UUID id,
        String username,
        String email,
        String fullName,
        UserRole role,
        AccountStatus status,
        LearningPreferences preferences) {

    public static ProfileResponse from(UserAccount account, UserProfile profile) {
        return new ProfileResponse(
                account.getId(),
                account.getUsername(),
                account.getEmail(),
                account.getFullName(),
                account.getRole(),
                account.getStatus(),
                profile == null ? null : LearningPreferences.from(profile));
    }

    public record LearningPreferences(
            String timeZone,
            String locale,
            int defaultDailyMinutes,
            String learningPreferences) {

        static LearningPreferences from(UserProfile profile) {
            return new LearningPreferences(
                    profile.getTimeZone(),
                    profile.getLocale(),
                    profile.getDefaultDailyMinutes(),
                    profile.getLearningPreferences());
        }
    }
}
