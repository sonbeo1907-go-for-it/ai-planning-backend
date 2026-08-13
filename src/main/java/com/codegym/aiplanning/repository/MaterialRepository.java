package com.codegym.aiplanning.repository;

import com.codegym.aiplanning.entity.material.Material;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MaterialRepository extends JpaRepository<Material, UUID> {

    @Modifying
    @Query("UPDATE Material m SET m.status = 'PROCESSING', m.processingStartedAt = :now WHERE m.id = :id AND m.status = 'PENDING'")
    int claimForProcessing(@Param("id") UUID id, @Param("now") java.time.Instant now);
}
