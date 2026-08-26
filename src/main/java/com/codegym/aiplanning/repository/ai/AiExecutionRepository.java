package com.codegym.aiplanning.repository.ai;

import com.codegym.aiplanning.entity.ai.AiExecution;
import com.codegym.aiplanning.entity.ai.AiExecutionStatus;
import com.codegym.aiplanning.entity.ai.AiExecutionTargetType;
import com.codegym.aiplanning.entity.ai.AiPurpose;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiExecutionRepository extends JpaRepository<AiExecution, UUID> {

    Optional<AiExecution> findByIdAndOwnerId(UUID id, UUID ownerId);

    Optional<AiExecution> findByOwnerIdAndIdempotencyKey(
            UUID ownerId, String idempotencyKey);

    Optional<AiExecution>
            findFirstByOwnerIdAndTargetTypeAndTargetIdAndPurposeAndStatusInOrderByCreatedAtDesc(
                    UUID ownerId,
                    AiExecutionTargetType targetType,
                    UUID targetId,
                    AiPurpose purpose,
                    List<AiExecutionStatus> statuses);

    Optional<AiExecution>
            findFirstByOwnerIdAndTargetTypeAndTargetIdAndPurposeOrderByCreatedAtDesc(
                    UUID ownerId,
                    AiExecutionTargetType targetType,
                    UUID targetId,
                    AiPurpose purpose);

    List<AiExecution> findTop25ByStatusOrderByCreatedAtAsc(AiExecutionStatus status);

    @EntityGraph(attributePaths = {"owner", "providerConfig", "providerConfig.provider"})
    @Query("select execution from AiExecution execution where execution.id = :id")
    Optional<AiExecution> findJobContextById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select execution from AiExecution execution where execution.id = :id")
    Optional<AiExecution> findByIdForUpdate(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update ai_executions
               set status = 'RUNNING',
                   attempt_count = attempt_count + 1,
                   started_at = coalesce(started_at, :startedAt),
                   lease_expires_at = :leaseExpiresAt,
                   updated_at = :startedAt,
                   version = version + 1
             where id = :id
               and status = 'QUEUED'
            """, nativeQuery = true)
    int claimQueued(
            @Param("id") UUID id,
            @Param("startedAt") Instant startedAt,
            @Param("leaseExpiresAt") Instant leaseExpiresAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update ai_executions
               set status = 'QUEUED',
                   lease_expires_at = null,
                   updated_at = :now,
                   version = version + 1
             where status = 'RUNNING'
               and lease_expires_at < :now
            """, nativeQuery = true)
    int requeueExpired(@Param("now") Instant now);
}
