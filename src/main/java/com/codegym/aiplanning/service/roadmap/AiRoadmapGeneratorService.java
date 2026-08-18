package com.codegym.aiplanning.service.roadmap;

import com.codegym.aiplanning.controller.roadmap.dto.RoadmapVersionResponse;

import java.util.UUID;

public interface AiRoadmapGeneratorService {

    /**
     * Generate initial AI Master Plan draft version for a roadmap.
     */
    RoadmapVersionResponse generate(UUID userId, UUID roadmapId);

    /**
     * Regenerate a new AI Master Plan draft version with optional adjustment instructions.
     */
    RoadmapVersionResponse regenerate(UUID userId, UUID roadmapId, String adjustmentPrompt);
}
