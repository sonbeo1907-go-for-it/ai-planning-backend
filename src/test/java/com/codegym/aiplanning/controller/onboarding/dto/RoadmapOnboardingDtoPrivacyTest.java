package com.codegym.aiplanning.controller.onboarding.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.codegym.aiplanning.entity.roadmap.ProficiencyLevel;
import com.codegym.aiplanning.entity.roadmap.RoadmapStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoadmapOnboardingDtoPrivacyTest {

    @Test
    void diagnosticStringsRedactPersonalLearningData() {
        String sensitiveGoal = "private-learning-goal";
        var request = new SaveRoadmapOnboardingRequest(
                sensitiveGoal, ProficiencyLevel.BASIC, 60, 90);
        var response = new RoadmapOnboardingResponse(
                UUID.randomUUID(),
                0,
                RoadmapStatus.ONBOARDING,
                sensitiveGoal,
                ProficiencyLevel.BASIC,
                60,
                90,
                false,
                null);

        assertThat(request.toString())
                .doesNotContain(sensitiveGoal, "BASIC", "dailyCommitmentMinutes")
                .contains("<redacted>");
        assertThat(response.toString())
                .doesNotContain(
                        sensitiveGoal,
                        "BASIC",
                        "dailyCommitmentMinutes",
                        "expectedDurationDays")
                .contains("<redacted>");
    }
}
