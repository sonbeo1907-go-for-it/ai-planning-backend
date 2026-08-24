package com.codegym.aiplanning.service.roadmap;

import com.codegym.aiplanning.entity.roadmap.ProficiencyLevel;
import java.util.List;
import java.util.UUID;

public record RoadmapGenerationContext(
        UUID roadmapId,
        String title,
        ProficiencyLevel proficiencyLevel,
        Integer dailyCommitmentMinutes,
        Integer expectedDurationDays,
        List<SourceDocument> sources) {

    public record SourceDocument(UUID id, String type, String content) {}
}
