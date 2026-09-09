package com.codegym.aiplanning.service.roadmap;

import com.codegym.aiplanning.controller.roadmap.dto.RoadmapProgressResponse;
import java.util.UUID;

public interface RoadmapProgressService {

    RoadmapProgressResponse calculateRoadmapProgress(UUID roadmapId, UUID activeVersionId, UUID userId);
}
