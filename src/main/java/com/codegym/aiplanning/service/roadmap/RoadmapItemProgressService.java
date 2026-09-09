package com.codegym.aiplanning.service.roadmap;

import com.codegym.aiplanning.controller.roadmap.dto.RoadmapItemProgressResponse;
import com.codegym.aiplanning.controller.roadmap.dto.StudyUnitResponse;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface RoadmapItemProgressService {

    record ItemProgressCalculationResult(
            RoadmapItemProgressResponse progress,
            List<StudyUnitResponse> studyUnits) {}

    ItemProgressCalculationResult calculateProgress(UUID roadmapItemId, UUID userId);

    RoadmapItemProgressResponse calculateItemProgress(UUID roadmapItemId, UUID userId);

    List<StudyUnitResponse> getStudyUnitsWithProgress(UUID roadmapItemId, UUID userId);

    Map<UUID, ItemProgressCalculationResult> calculateItemsProgress(
            Collection<UUID> roadmapItemIds, UUID userId);
}
