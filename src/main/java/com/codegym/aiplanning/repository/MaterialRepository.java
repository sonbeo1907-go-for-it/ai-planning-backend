package com.codegym.aiplanning.repository;

import com.codegym.aiplanning.entity.material.Material;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import java.util.List;
import com.codegym.aiplanning.entity.material.MaterialStatus;
import com.codegym.aiplanning.entity.material.MaterialType;

@Repository
public interface MaterialRepository extends JpaRepository<Material, UUID> {

    @Query("select material from Material material "
            + "where material.user.id = :userId "
            + "and material.archivedAt is null "
            + "and (:type is null or material.type = :type) "
            + "and (:status is null or material.status = :status)")
    Page<Material> listOwnedActive(
            @Param("userId") UUID userId,
            @Param("type") MaterialType type,
            @Param("status") MaterialStatus status,
            Pageable pageable);

    @Query("select material from Material material "
            + "where material.user.id = :userId "
            + "and material.archivedAt is null "
            + "and (:type is null or material.type = :type) "
            + "and (:status is null or material.status = :status) "
            + "and (lower(coalesce(material.originalFileName, '')) "
            + "like lower(concat('%', :query, '%')) "
            + "or lower(coalesce(material.content, '')) "
            + "like lower(concat('%', :query, '%'))) ")
    Page<Material> searchOwnedActive(
            @Param("userId") UUID userId,
            @Param("query") String query,
            @Param("type") MaterialType type,
            @Param("status") MaterialStatus status,
            Pageable pageable);

    Optional<Material> findByIdAndUserId(UUID id, UUID userId);

    List<Material> findAllByIdInAndUserIdAndArchivedAtIsNull(
            List<UUID> ids, UUID userId);

    @Modifying
    @Query("""
            UPDATE Material m
            SET m.status = 'PROCESSING', m.processingStartedAt = :now
            WHERE m.id = :id
              AND m.status = 'PENDING'
              AND m.archivedAt IS NULL
            """)
    int claimForProcessing(@Param("id") UUID id, @Param("now") java.time.Instant now);
}
