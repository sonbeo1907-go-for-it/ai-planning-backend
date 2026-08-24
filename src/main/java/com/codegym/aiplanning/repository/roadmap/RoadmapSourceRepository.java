package com.codegym.aiplanning.repository.roadmap;

import com.codegym.aiplanning.entity.roadmap.RoadmapSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoadmapSourceRepository extends JpaRepository<RoadmapSource, UUID> {

    @Query("select link from RoadmapSource link "
            + "join fetch link.learningSource source "
            + "where link.roadmap.id = :roadmapId and source.sourceType = "
            + "com.codegym.aiplanning.entity.source.LearningSourceType.GOAL")
    Optional<RoadmapSource> findGoalSourceByRoadmapId(@Param("roadmapId") UUID roadmapId);

    @Query("""
            select link
            from RoadmapSource link
            left join fetch link.learningSource
            left join fetch link.material
            where link.roadmap.id = :roadmapId
            order by link.createdAt
            """)
    List<RoadmapSource> findByRoadmapId(@Param("roadmapId") UUID roadmapId);
}
