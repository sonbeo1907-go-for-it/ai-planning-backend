package com.codegym.aiplanning.repository.ai;

import com.codegym.aiplanning.entity.ai.AiProviderCredential;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiProviderCredentialRepository
        extends JpaRepository<AiProviderCredential, UUID> {

    @EntityGraph(attributePaths = "provider")
    List<AiProviderCredential> findAllByArchivedAtIsNullOrderByProviderCodeAscPriorityDesc();

    @EntityGraph(attributePaths = "provider")
    List<AiProviderCredential>
            findAllByProviderIdAndArchivedAtIsNullOrderByPriorityDescCreatedAtAsc(UUID providerId);

    @EntityGraph(attributePaths = "provider")
    List<AiProviderCredential>
            findAllByProviderIdAndEnabledTrueAndArchivedAtIsNullOrderByPriorityDescCreatedAtAsc(
                    UUID providerId);

    long countByProviderIdAndEnabledTrueAndArchivedAtIsNull(UUID providerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select credential
            from AiProviderCredential credential
            join fetch credential.provider provider
            where credential.id = :credentialId
              and provider.id = :providerId
              and credential.archivedAt is null
              and provider.archivedAt is null
            """)
    Optional<AiProviderCredential> findActiveByIdForUpdate(
            @Param("providerId") UUID providerId,
            @Param("credentialId") UUID credentialId);
}
