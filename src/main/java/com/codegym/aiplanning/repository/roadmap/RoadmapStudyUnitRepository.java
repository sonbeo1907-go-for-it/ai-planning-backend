package com.codegym.aiplanning.repository.roadmap;

import com.codegym.aiplanning.entity.roadmap.RoadmapStudyUnit;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoadmapStudyUnitRepository extends JpaRepository<RoadmapStudyUnit, UUID> {

    List<RoadmapStudyUnit> findAllByRoadmapItemIdOrderByOrderIndexAsc(UUID roadmapItemId);

    List<RoadmapStudyUnit> findAllByRoadmapVersionIdAndRoadmapItemIdOrderByOrderIndexAsc(
            UUID roadmapVersionId, UUID roadmapItemId);

    List<RoadmapStudyUnit> findAllByRoadmapItemIdInOrderByOrderIndexAsc(
            Collection<UUID> roadmapItemIds);
}
