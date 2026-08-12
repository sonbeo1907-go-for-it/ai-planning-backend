package com.codegym.aiplanning.repository.auth;

import com.codegym.aiplanning.entity.auth.RefreshToken;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshToken token "
            + "join fetch token.session session "
            + "join fetch session.user "
            + "where token.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("update RefreshToken token set token.revokedAt = :now "
            + "where token.session.id = :sessionId and token.revokedAt is null")
    int revokeAllBySessionId(@Param("sessionId") UUID sessionId, @Param("now") Instant now);

    @Modifying
    @Query("update RefreshToken token set token.revokedAt = :now "
            + "where token.session.id in :sessionIds and token.revokedAt is null")
    int revokeAllBySessionIds(
            @Param("sessionIds") Collection<UUID> sessionIds,
            @Param("now") Instant now);
}
