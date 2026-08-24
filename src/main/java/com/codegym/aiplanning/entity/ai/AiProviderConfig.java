package com.codegym.aiplanning.entity.ai;

import com.codegym.aiplanning.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ai_provider_configs")
public class AiProviderConfig extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private AiProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AiPurpose purpose;

    @Column(nullable = false, length = 150)
    private String model;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "default_provider", nullable = false)
    private boolean defaultProvider;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_slot_purpose", length = 50)
    private AiPurpose defaultSlotPurpose;

    @Column(name = "timeout_seconds", nullable = false)
    private int timeoutSeconds;

    @Column(name = "max_input_tokens", nullable = false)
    private int maxInputTokens;

    @Column(name = "max_output_tokens", nullable = false)
    private int maxOutputTokens;

    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal temperature;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected AiProviderConfig() {}

    public static AiProviderConfig create(
            AiProvider provider,
            AiPurpose purpose,
            String model,
            boolean enabled,
            int timeoutSeconds,
            int maxInputTokens,
            int maxOutputTokens,
            BigDecimal temperature) {
        AiProviderConfig config = new AiProviderConfig();
        config.provider = provider;
        config.purpose = purpose;
        config.model = model;
        config.enabled = enabled;
        config.timeoutSeconds = timeoutSeconds;
        config.maxInputTokens = maxInputTokens;
        config.maxOutputTokens = maxOutputTokens;
        config.temperature = temperature;
        return config;
    }

    public void update(
            String model,
            int timeoutSeconds,
            int maxInputTokens,
            int maxOutputTokens,
            BigDecimal temperature) {
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
        this.maxInputTokens = maxInputTokens;
        this.maxOutputTokens = maxOutputTokens;
        this.temperature = temperature;
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }

    public void makeDefault() {
        if (!enabled || isArchived() || !provider.isEnabled() || provider.isArchived()) {
            throw new IllegalStateException(
                    "Only an enabled configuration on an enabled provider can be default.");
        }
        this.defaultProvider = true;
        this.defaultSlotPurpose = purpose;
    }

    public void clearDefault() {
        this.defaultProvider = false;
        this.defaultSlotPurpose = null;
    }

    public void archive(Instant archivedAt) {
        clearDefault();
        disable();
        this.archivedAt = archivedAt;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public AiProvider getProvider() {
        return provider;
    }

    public AiPurpose getPurpose() {
        return purpose;
    }

    public String getModel() {
        return model;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isDefaultProvider() {
        return defaultProvider;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public int getMaxInputTokens() {
        return maxInputTokens;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public BigDecimal getTemperature() {
        return temperature;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }
}
