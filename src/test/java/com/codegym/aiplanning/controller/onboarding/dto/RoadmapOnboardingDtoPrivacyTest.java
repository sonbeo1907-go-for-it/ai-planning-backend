package com.codegym.aiplanning.controller.onboarding.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.codegym.aiplanning.entity.roadmap.ProficiencyLevel;
import com.codegym.aiplanning.entity.roadmap.RoadmapStatus;
import com.codegym.aiplanning.entity.roadmap.RoadmapTitleOrigin;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoadmapOnboardingDtoPrivacyTest {

    @Test
    void diagnosticStringsRedactPersonalLearningData() {
        String sensitiveGoal = "private-learning-goal";
        String sensitiveTitle = "private-roadmap-title";
        var request = new SaveRoadmapOnboardingRequest(
                sensitiveTitle, sensitiveGoal, ProficiencyLevel.BASIC, 60, 90, 0L);
        var response = new RoadmapOnboardingResponse(
                UUID.randomUUID(),
                0,
                RoadmapStatus.ONBOARDING,
                sensitiveTitle,
                RoadmapTitleOrigin.USER,
                sensitiveGoal,
                ProficiencyLevel.BASIC,
                60,
                90,
                false,
                null);

        assertThat(request.toString())
                .doesNotContain(
                        sensitiveGoal,
                        sensitiveTitle,
                        "BASIC",
                        "dailyCommitmentMinutes")
                .contains("<redacted>");
        assertThat(response.toString())
                .doesNotContain(
                        sensitiveGoal,
                        sensitiveTitle,
                        "BASIC",
                        "dailyCommitmentMinutes",
                        "expectedDurationDays")
                .contains("<redacted>");
    }
}
