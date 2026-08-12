package com.codegym.aiplanning.repository.roadmap;

import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoadmapRepository extends JpaRepository<Roadmap, UUID> {

    Optional<Roadmap> findByOwnerIdAndStatus(UUID ownerId, RoadmapStatus status);

    long countByOwnerId(UUID ownerId);

    List<Roadmap> findAllByOwnerIdOrderByUpdatedAtDesc(UUID ownerId);

    Optional<Roadmap> findByIdAndOwnerId(UUID roadmapId, UUID ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select roadmap from Roadmap roadmap "
            + "where roadmap.id = :roadmapId and roadmap.owner.id = :ownerId")
    Optional<Roadmap> findOwnedByIdForUpdate(
            @Param("roadmapId") UUID roadmapId, @Param("ownerId") UUID ownerId);
}
