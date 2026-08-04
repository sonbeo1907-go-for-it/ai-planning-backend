package com.codegym.aiplanning.repository.auth;

import com.codegym.aiplanning.entity.auth.AuthSession;
import com.codegym.aiplanning.entity.auth.AuthSessionStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    boolean existsByIdAndStatusAndExpiresAtAfter(
            UUID id, AuthSessionStatus status, Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from AuthSession session where session.id = :id")
    Optional<AuthSession> findByIdForUpdate(@Param("id") UUID id);

    @Query("select session from AuthSession session where session.user.id = :userId and session.status = :status and session.id != :sessionId")
    java.util.List<AuthSession> findActiveSessionsExcept(
            @Param("userId") UUID userId,
            @Param("status") AuthSessionStatus status,
            @Param("sessionId") UUID sessionId);
}
