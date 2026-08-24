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

@Repository
public interface MaterialRepository extends JpaRepository<Material, UUID> {

    Page<Material> findByUserIdAndArchivedAtIsNull(UUID userId, Pageable pageable);

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
