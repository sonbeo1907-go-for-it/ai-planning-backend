package com.codegym.aiplanning.service.daily;

import com.codegym.aiplanning.common.validation.StudyTimeBudgetPolicy;
import com.codegym.aiplanning.entity.profile.UserProfile;
import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.repository.profile.UserProfileRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AvailableMinutesResolver {

    private final UserProfileRepository userProfileRepository;

    public AvailableMinutesResolver(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    public ResolvedAvailableMinutes resolve(
            UUID userId,
            Integer explicitMinutes,
            Roadmap selectedRoadmap) {
        if (explicitMinutes != null) {
            return new ResolvedAvailableMinutes(
                    StudyTimeBudgetPolicy.requireValid(
                            explicitMinutes, "Available minutes"),
                    AvailableMinutesSource.EXPLICIT_DAY);
        }

        if (selectedRoadmap != null && selectedRoadmap.getDailyCommitmentMinutes() != null) {
            return new ResolvedAvailableMinutes(
                    StudyTimeBudgetPolicy.requireValid(
                            selectedRoadmap.getDailyCommitmentMinutes(),
                            "Roadmap daily commitment"),
                    AvailableMinutesSource.ROADMAP);
        }

        Integer profileDefault = userProfileRepository.findByUserId(userId)
                .map(UserProfile::getDefaultDailyMinutes)
                .orElse(null);
        if (profileDefault != null) {
            return new ResolvedAvailableMinutes(
                    StudyTimeBudgetPolicy.requireValid(
                            profileDefault, "Profile default daily minutes"),
                    AvailableMinutesSource.PROFILE);
        }

        return new ResolvedAvailableMinutes(
                StudyTimeBudgetPolicy.SYSTEM_FALLBACK_MINUTES,
                AvailableMinutesSource.SYSTEM_FALLBACK);
    }
}
