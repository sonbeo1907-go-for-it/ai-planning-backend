package com.codegym.aiplanning.repository.ai;

import com.codegym.aiplanning.entity.ai.AiProvider;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiProviderRepository extends JpaRepository<AiProvider, UUID> {

    List<AiProvider> findAllByArchivedAtIsNullOrderByCodeAsc();

    Optional<AiProvider> findByIdAndArchivedAtIsNull(UUID id);

    boolean existsByCodeIgnoreCase(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select provider
            from AiProvider provider
            where provider.id = :id
              and provider.archivedAt is null
            """)
    Optional<AiProvider> findActiveByIdForUpdate(@Param("id") UUID id);
}
