package com.codegym.aiplanning.controller.onboarding.dto;

import com.codegym.aiplanning.entity.source.LearningSource;
import com.codegym.aiplanning.entity.roadmap.ProficiencyLevel;
import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RoadmapOnboardingResponse(
        UUID roadmapId,
        long version,
        RoadmapStatus status,
        String goal,
        ProficiencyLevel proficiencyLevel,
        Integer dailyCommitmentMinutes,
        Integer expectedDurationDays,
        boolean completed,
        Instant completedAt) {

    public static RoadmapOnboardingResponse from(
            Roadmap roadmap, LearningSource goalSource) {
        return new RoadmapOnboardingResponse(
                roadmap.getId(),
                roadmap.getVersion(),
                roadmap.getStatus(),
                goalSource.getContentText(),
                roadmap.getProficiencyLevel(),
                roadmap.getDailyCommitmentMinutes(),
                roadmap.getExpectedDurationDays(),
                roadmap.isOnboardingComplete(),
                roadmap.getOnboardingCompletedAt());
    }

    @Override
    public String toString() {
        return "RoadmapOnboardingResponse[roadmapId=" + roadmapId
                + ", status=" + status
                + ", completed=" + completed
                + ", personalLearningData=<redacted>]";
    }
}
