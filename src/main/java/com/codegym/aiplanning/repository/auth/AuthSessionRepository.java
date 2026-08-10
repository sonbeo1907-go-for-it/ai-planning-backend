package com.codegym.aiplanning.repository.auth;

import com.codegym.aiplanning.entity.auth.AuthSession;
import com.codegym.aiplanning.entity.auth.AuthSessionStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    boolean existsByIdAndStatusAndUser_StatusAndExpiresAtAfter(
            UUID id, AuthSessionStatus status, com.codegym.aiplanning.entity.auth.AccountStatus userStatus, Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from AuthSession session where session.id = :id")
    Optional<AuthSession> findByIdForUpdate(@Param("id") UUID id);

    @Query("select session from AuthSession session join fetch session.user where session.id = :id")
    Optional<AuthSession> findByIdWithUser(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from AuthSession session "
            + "where session.user.id = :userId "
            + "and session.status = :status "
            + "and session.expiresAt > :now")
    List<AuthSession> findAllActiveByUserIdForUpdate(
            @Param("userId") UUID userId,
            @Param("status") AuthSessionStatus status,
            @Param("now") Instant now);

    @Query("select session from AuthSession session where session.user.id = :userId and session.status = :status and session.id != :sessionId")
    java.util.List<AuthSession> findActiveSessionsExcept(
            @Param("userId") UUID userId,
            @Param("status") AuthSessionStatus status,
            @Param("sessionId") UUID sessionId);
}
