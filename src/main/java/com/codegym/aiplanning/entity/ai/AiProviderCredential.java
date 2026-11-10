package com.codegym.aiplanning.entity.ai;

import com.codegym.aiplanning.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "ai_provider_credentials")
public class AiProviderCredential extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private AiProvider provider;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(name = "secret_ref", nullable = false, length = 150)
    private String secretRef;

    @Column(nullable = false)
    private int priority;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected AiProviderCredential() {}

    public static AiProviderCredential create(
            AiProvider provider,
            String label,
            String secretRef,
            int priority,
            boolean enabled) {
        AiProviderCredential credential = new AiProviderCredential();
        credential.provider = provider;
        credential.label = label;
        credential.secretRef = secretRef;
        credential.priority = priority;
        credential.enabled = enabled;
        return credential;
    }

    public void update(String label, String secretRef, int priority) {
        this.label = label;
        this.secretRef = secretRef;
        this.priority = priority;
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

    public AiProvider getProvider() {
        return provider;
    }

    public String getLabel() {
        return label;
    }

    public String getSecretRef() {
        return secretRef;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }
}
