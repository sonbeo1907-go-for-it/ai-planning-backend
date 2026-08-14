package com.codegym.aiplanning.repository.roadmap;

import com.codegym.aiplanning.entity.roadmap.RoadmapItem;
import com.codegym.aiplanning.entity.roadmap.RoadmapItemType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoadmapItemRepository extends JpaRepository<RoadmapItem, UUID> {

    List<RoadmapItem> findAllByRoadmapVersionIdOrderByOrderIndexAsc(UUID versionId);

    @Query("select item from RoadmapItem item "
            + "join fetch item.roadmapVersion version "
            + "left join fetch item.parent parent "
            + "where version.id in :versionIds "
            + "order by version.id, item.orderIndex")
    List<RoadmapItem> findAllByRoadmapVersionIds(
            @Param("versionIds") List<UUID> versionIds);

    List<RoadmapItem> findAllByRoadmapVersionIdAndItemTypeAndParentIsNullOrderByOrderIndexAsc(
            UUID versionId, RoadmapItemType itemType);

    List<RoadmapItem> findAllByRoadmapVersionIdAndParentIdOrderByOrderIndexAsc(
            UUID versionId, UUID parentId);

    Optional<RoadmapItem> findByIdAndRoadmapVersionId(UUID itemId, UUID versionId);

    long countByRoadmapVersionIdAndItemType(UUID versionId, RoadmapItemType itemType);

    void deleteAllByParentId(UUID parentId);
}
