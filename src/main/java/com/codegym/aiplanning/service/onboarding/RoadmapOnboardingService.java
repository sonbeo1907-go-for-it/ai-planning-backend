package com.codegym.aiplanning.service.onboarding;

import com.codegym.aiplanning.controller.onboarding.dto.RoadmapOnboardingResponse;
import com.codegym.aiplanning.controller.onboarding.dto.SaveRoadmapOnboardingRequest;
import java.util.UUID;

public interface RoadmapOnboardingService {

    RoadmapOnboardingResponse startOrResume(UUID userId);

    RoadmapOnboardingResponse getCurrent(UUID userId);

    RoadmapOnboardingResponse get(UUID userId, UUID roadmapId);

    RoadmapOnboardingResponse save(
            UUID userId, UUID roadmapId, SaveRoadmapOnboardingRequest request);

    RoadmapOnboardingResponse complete(UUID userId, UUID roadmapId);
}
