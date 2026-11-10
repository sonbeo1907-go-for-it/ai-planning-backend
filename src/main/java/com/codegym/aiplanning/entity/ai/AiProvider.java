package com.codegym.aiplanning.entity.ai;

import com.codegym.aiplanning.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "ai_providers")
public class AiProvider extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50, updatable = false)
    private String code;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "base_url", nullable = false, length = 500)
    private String baseUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AiProviderProtocol protocol;

    @Enumerated(EnumType.STRING)
    @Column(name = "credential_strategy", nullable = false, length = 30)
    private CredentialSelectionStrategy credentialStrategy;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected AiProvider() {}

    public static AiProvider create(
            String code,
            String displayName,
            String baseUrl,
            AiProviderProtocol protocol,
            CredentialSelectionStrategy credentialStrategy,
            boolean enabled) {
        AiProvider provider = new AiProvider();
        provider.code = code;
        provider.displayName = displayName;
        provider.baseUrl = baseUrl;
        provider.protocol = protocol;
        provider.credentialStrategy = credentialStrategy;
        provider.enabled = enabled;
        return provider;
    }

    public void update(
            String displayName,
            String baseUrl,
            AiProviderProtocol protocol,
            CredentialSelectionStrategy credentialStrategy) {
        this.displayName = displayName;
        this.baseUrl = baseUrl;
        this.protocol = protocol;
        this.credentialStrategy = credentialStrategy;
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }

    public void archive(Instant archivedAt) {
        this.enabled = false;
        this.archivedAt = archivedAt;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public AiProviderProtocol getProtocol() {
        return protocol;
    }

    public CredentialSelectionStrategy getCredentialStrategy() {
        return credentialStrategy;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }
}
