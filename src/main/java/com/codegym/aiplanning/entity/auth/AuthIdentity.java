package com.codegym.aiplanning.entity.auth;

import com.codegym.aiplanning.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Locale;

@Entity
@Table(
        name = "auth_identities",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_auth_identities_provider_subject",
                    columnNames = {"provider", "provider_subject"}),
            @UniqueConstraint(
                    name = "uk_auth_identities_user_provider",
                    columnNames = {"user_id", "provider"})
        })
public class AuthIdentity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AuthProvider provider;

    @Column(name = "provider_subject", nullable = false, length = 255)
    private String providerSubject;

    @Column(name = "provider_email", nullable = false, length = 254)
    private String providerEmail;

    protected AuthIdentity() {}

    public static AuthIdentity google(
            UserAccount user, String providerSubject, String providerEmail) {
        AuthIdentity identity = new AuthIdentity();
        identity.user = user;
        identity.provider = AuthProvider.GOOGLE;
        identity.providerSubject = providerSubject;
        identity.providerEmail = providerEmail.trim().toLowerCase(Locale.ROOT);
        return identity;
    }

    public UserAccount getUser() {
        return user;
    }

    public AuthProvider getProvider() {
        return provider;
    }

    public String getProviderSubject() {
        return providerSubject;
    }

    public String getProviderEmail() {
        return providerEmail;
    }
}
