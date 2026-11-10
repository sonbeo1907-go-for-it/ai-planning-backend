package com.codegym.aiplanning.repository.auth;

import com.codegym.aiplanning.entity.auth.AuthIdentity;
import com.codegym.aiplanning.entity.auth.AuthProvider;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthIdentityRepository extends JpaRepository<AuthIdentity, UUID> {

    @Query("select identity from AuthIdentity identity "
            + "join fetch identity.user "
            + "where identity.provider = :provider "
            + "and identity.providerSubject = :providerSubject")
    Optional<AuthIdentity> findByProviderAndProviderSubject(
            @Param("provider") AuthProvider provider,
            @Param("providerSubject") String providerSubject);

    boolean existsByUserIdAndProvider(UUID userId, AuthProvider provider);
}
