package com.codegym.aiplanning.repository.roadmap;

import com.codegym.aiplanning.entity.roadmap.RoadmapVersion;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersionStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoadmapVersionRepository extends JpaRepository<RoadmapVersion, UUID> {

    List<RoadmapVersion> findAllByRoadmapIdOrderByVersionNumberDesc(UUID roadmapId);

    Optional<RoadmapVersion> findByRoadmapIdAndStatus(
            UUID roadmapId, RoadmapVersionStatus status);

    Optional<RoadmapVersion> findByIdAndRoadmapId(UUID versionId, UUID roadmapId);

    Optional<RoadmapVersion> findFirstByRoadmapIdOrderByVersionNumberDesc(UUID roadmapId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select version from RoadmapVersion version "
            + "where version.id = :versionId and version.roadmap.id = :roadmapId")
    Optional<RoadmapVersion> findByIdAndRoadmapIdForUpdate(
            @Param("versionId") UUID versionId, @Param("roadmapId") UUID roadmapId);
}
