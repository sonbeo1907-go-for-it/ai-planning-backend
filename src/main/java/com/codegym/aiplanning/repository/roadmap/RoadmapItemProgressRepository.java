package com.codegym.aiplanning.repository.roadmap;

import com.codegym.aiplanning.entity.roadmap.RoadmapItemProgress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoadmapItemProgressRepository
        extends JpaRepository<RoadmapItemProgress, UUID> {

    Optional<RoadmapItemProgress> findByUserIdAndRoadmapItemId(
            UUID userId, UUID roadmapItemId);

    List<RoadmapItemProgress> findByUserIdAndRoadmapItemIdIn(
            UUID userId, List<UUID> roadmapItemIds);

    List<RoadmapItemProgress> findByUserIdAndRoadmapVersionId(
            UUID userId, UUID roadmapVersionId);
}
