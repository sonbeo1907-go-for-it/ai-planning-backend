package com.codegym.aiplanning.repository.ai;

import com.codegym.aiplanning.entity.ai.AiProviderConfig;
import com.codegym.aiplanning.entity.ai.AiPurpose;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiProviderConfigRepository extends JpaRepository<AiProviderConfig, UUID> {

    @EntityGraph(attributePaths = "provider")
    List<AiProviderConfig>
            findAllByArchivedAtIsNullOrderByPurposeAscProviderCodeAscModelAsc();

    @EntityGraph(attributePaths = "provider")
    Optional<AiProviderConfig> findByIdAndArchivedAtIsNull(UUID id);

    @EntityGraph(attributePaths = "provider")
    Optional<AiProviderConfig> findByPurposeAndDefaultProviderTrueAndArchivedAtIsNull(
            AiPurpose purpose);

    boolean existsByProviderIdAndArchivedAtIsNull(UUID providerId);

    boolean existsByProviderIdAndDefaultProviderTrueAndArchivedAtIsNull(UUID providerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select config
            from AiProviderConfig config
            join fetch config.provider
            where config.id = :id
              and config.archivedAt is null
            """)
    Optional<AiProviderConfig> findActiveByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select config
            from AiProviderConfig config
            join fetch config.provider
            where config.purpose = :purpose
              and config.defaultProvider = true
              and config.archivedAt is null
            """)
    Optional<AiProviderConfig> findDefaultByPurposeForUpdate(
            @Param("purpose") AiPurpose purpose);
}
