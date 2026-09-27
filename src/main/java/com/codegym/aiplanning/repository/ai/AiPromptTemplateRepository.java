package com.codegym.aiplanning.repository.ai;

import com.codegym.aiplanning.entity.ai.AiPromptTemplate;
import com.codegym.aiplanning.entity.ai.AiPurpose;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiPromptTemplateRepository extends JpaRepository<AiPromptTemplate, UUID> {

    Optional<AiPromptTemplate> findByPurposeAndIsActiveTrue(AiPurpose purpose);

    List<AiPromptTemplate> findByPurposeOrderByVersionNumberDesc(AiPurpose purpose);

    Optional<AiPromptTemplate> findByPurposeAndVersionNumber(AiPurpose purpose, int versionNumber);

    @Query("SELECT COALESCE(MAX(p.versionNumber), 0) FROM AiPromptTemplate p WHERE p.purpose = :purpose")
    int findMaxVersionNumberByPurpose(@Param("purpose") AiPurpose purpose);

    Optional<AiPromptTemplate> findFirstByPurposeAndIsSystemTrueOrderByVersionNumberDesc(AiPurpose purpose);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM AiPromptTemplate p WHERE p.id = :id")
    Optional<AiPromptTemplate> findByIdForUpdate(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE AiPromptTemplate p
               SET p.isActive = false,
                   p.activeSlotPurpose = null,
                   p.updatedAt = :now,
                   p.version = p.version + 1
             WHERE p.purpose = :purpose
               AND p.isActive = true
            """)
    int deactivateCurrentActive(
            @Param("purpose") AiPurpose purpose,
            @Param("now") Instant now);
}
