package com.codegym.aiplanning.service.roadmap;

import com.codegym.aiplanning.controller.roadmap.dto.RoadmapProgressResponse;
import com.codegym.aiplanning.entity.daily.ProgressEntry;
import com.codegym.aiplanning.entity.daily.ProgressEntryStatus;
import java.util.UUID;

public interface RoadmapProgressService {

    void recordOutcome(
            UUID userId,
            UUID roadmapItemId,
            ProgressEntry progressEntry,
            ProgressEntryStatus outcome);

    RoadmapProgressResponse getProgress(UUID userId, UUID roadmapId);
}
