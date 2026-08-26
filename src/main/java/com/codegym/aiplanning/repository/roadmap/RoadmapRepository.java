package com.codegym.aiplanning.repository.roadmap;

import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoadmapRepository extends JpaRepository<Roadmap, UUID> {

    Optional<Roadmap> findByOwnerIdAndStatus(UUID ownerId, RoadmapStatus status);

    long countByOwnerId(UUID ownerId);

    List<Roadmap> findAllByOwnerIdOrderByUpdatedAtDesc(UUID ownerId);

    Page<Roadmap> findByOwnerId(UUID ownerId, Pageable pageable);

    Page<Roadmap> findByOwnerIdAndStatus(
            UUID ownerId, RoadmapStatus status, Pageable pageable);

    @Query("select roadmap from Roadmap roadmap "
            + "where roadmap.owner.id = :ownerId "
            + "and (lower(coalesce(roadmap.title, '')) like lower(concat('%', :query, '%')) "
            + "or lower(coalesce(roadmap.description, '')) like lower(concat('%', :query, '%'))) ")
    Page<Roadmap> searchOwnedByQuery(
            @Param("ownerId") UUID ownerId,
            @Param("query") String query,
            Pageable pageable);

    @Query("select roadmap from Roadmap roadmap "
            + "where roadmap.owner.id = :ownerId "
            + "and roadmap.status = :status "
            + "and (lower(coalesce(roadmap.title, '')) like lower(concat('%', :query, '%')) "
            + "or lower(coalesce(roadmap.description, '')) like lower(concat('%', :query, '%'))) ")
    Page<Roadmap> searchOwnedByQueryAndStatus(
            @Param("ownerId") UUID ownerId,
            @Param("query") String query,
            @Param("status") RoadmapStatus status,
            Pageable pageable);

    Optional<Roadmap> findByIdAndOwnerId(UUID roadmapId, UUID ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select roadmap from Roadmap roadmap "
            + "where roadmap.id = :roadmapId and roadmap.owner.id = :ownerId")
    Optional<Roadmap> findOwnedByIdForUpdate(
            @Param("roadmapId") UUID roadmapId, @Param("ownerId") UUID ownerId);
}
