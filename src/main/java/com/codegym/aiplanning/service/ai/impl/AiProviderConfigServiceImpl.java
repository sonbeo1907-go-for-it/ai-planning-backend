package com.codegym.aiplanning.service.ai.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.admin.ai.dto.AiProviderConfigResponse;
import com.codegym.aiplanning.controller.admin.ai.dto.AiProviderConnectionTestResponse;
import com.codegym.aiplanning.controller.admin.ai.dto.CreateAiProviderConfigRequest;
import com.codegym.aiplanning.controller.admin.ai.dto.UpdateAiProviderConfigRequest;
import com.codegym.aiplanning.entity.ai.AiProvider;
import com.codegym.aiplanning.entity.ai.AiProviderConfig;
import com.codegym.aiplanning.entity.ai.AiPurpose;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.repository.ai.AiProviderConfigRepository;
import com.codegym.aiplanning.repository.ai.AiProviderCredentialRepository;
import com.codegym.aiplanning.repository.ai.AiProviderRepository;
import com.codegym.aiplanning.service.ai.AiCredentialSelector;
import com.codegym.aiplanning.service.ai.AiProviderConfigService;
import com.codegym.aiplanning.service.ai.AiProviderSelector;
import com.codegym.aiplanning.service.ai.ResolvedAiCredential;
import com.codegym.aiplanning.service.ai.provider.AiProviderConnectionGateway;
import com.codegym.aiplanning.service.ai.provider.ProviderConnectionResult;
import com.codegym.aiplanning.service.audit.AuditLogService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiProviderConfigServiceImpl
        implements AiProviderConfigService, AiProviderSelector {

    private static final String TARGET_RESOURCE = "AiProviderConfig";

    private final AiProviderConfigRepository repository;
    private final AiProviderRepository providerRepository;
    private final AiProviderCredentialRepository credentialRepository;
    private final AiProviderConfigurationPolicy configurationPolicy;
    private final AiCredentialSelector credentialSelector;
    private final AiProviderConnectionGateway connectionGateway;
    private final AuditLogService auditLogService;

    public AiProviderConfigServiceImpl(
            AiProviderConfigRepository repository,
            AiProviderRepository providerRepository,
            AiProviderCredentialRepository credentialRepository,
            AiProviderConfigurationPolicy configurationPolicy,
            AiCredentialSelector credentialSelector,
            AiProviderConnectionGateway connectionGateway,
            AuditLogService auditLogService) {
        this.repository = repository;
        this.providerRepository = providerRepository;
        this.credentialRepository = credentialRepository;
        this.configurationPolicy = configurationPolicy;
        this.credentialSelector = credentialSelector;
        this.connectionGateway = connectionGateway;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    public AiProviderConfigResponse create(
            UUID actorId, String actorEmail, CreateAiProviderConfigRequest request) {
        validateDefaultState(request.enabled(), request.defaultProvider());
        AiProvider provider = requireProvider(request.providerId());
        if (request.enabled() && !provider.isEnabled()) {
            throw invalid("An enabled configuration requires an enabled provider.");
        }
        if (request.defaultProvider()) {
            requireProviderReady(provider);
        }

        Optional<AiProviderConfig> currentDefault =
                repository.findDefaultByPurposeForUpdate(request.purpose());
        if (request.enabled() && !request.defaultProvider() && currentDefault.isEmpty()) {
            throw defaultRequired(
                    "The first enabled configuration for a purpose must be its default.");
        }

        AiProviderConfig config = AiProviderConfig.create(
                provider,
                request.purpose(),
                configurationPolicy.normalizeModel(request.model()),
                request.enabled(),
                request.timeoutSeconds(),
                request.maxInputTokens(),
                request.maxOutputTokens(),
                request.temperature());

        if (request.defaultProvider()) {
            currentDefault.ifPresent(this::clearAndFlushDefault);
            config.makeDefault();
        }

        AiProviderConfig saved = repository.saveAndFlush(config);
        audit(actorId, actorEmail, AuditEventAction.AI_PROVIDER_CONFIG_CREATED, saved);
        return response(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiProviderConfigResponse> list() {
        return repository.findAllByArchivedAtIsNullOrderByPurposeAscProviderCodeAscModelAsc()
                .stream()
                .map(this::response)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AiProviderConfigResponse get(UUID configId) {
        return response(requireActive(configId));
    }

    @Override
    @Transactional
    public AiProviderConfigResponse update(
            UUID actorId,
            String actorEmail,
            UUID configId,
            UpdateAiProviderConfigRequest request) {
        AiProviderConfig config = requireActiveForUpdate(configId);
        requireVersion(config, request.version());
        config.update(
                configurationPolicy.normalizeModel(request.model()),
                request.timeoutSeconds(),
                request.maxInputTokens(),
                request.maxOutputTokens(),
                request.temperature());
        AiProviderConfig saved = repository.saveAndFlush(config);
        audit(actorId, actorEmail, AuditEventAction.AI_PROVIDER_CONFIG_UPDATED, saved);
        return response(saved);
    }

    @Override
    @Transactional
    public AiProviderConfigResponse enable(
            UUID actorId, String actorEmail, UUID configId, long expectedVersion) {
        AiProviderConfig config = requireActiveForUpdate(configId);
        requireVersion(config, expectedVersion);
        if (config.isEnabled()) {
            return response(config);
        }
        if (!config.getProvider().isEnabled()) {
            throw invalid("Enable the provider before enabling this configuration.");
        }

        boolean hasDefault = repository
                .findDefaultByPurposeForUpdate(config.getPurpose())
                .isPresent();
        if (!hasDefault) {
            throw defaultRequired(
                    "Set this configuration as default instead of enabling a purpose without a default.");
        }

        config.enable();
        AiProviderConfig saved = repository.saveAndFlush(config);
        audit(actorId, actorEmail, AuditEventAction.AI_PROVIDER_CONFIG_ENABLED, saved);
        return response(saved);
    }

    @Override
    @Transactional
    public AiProviderConfigResponse disable(
            UUID actorId, String actorEmail, UUID configId, long expectedVersion) {
        AiProviderConfig config = requireActiveForUpdate(configId);
        requireVersion(config, expectedVersion);
        if (config.isDefaultProvider()) {
            throw defaultRequired(
                    "The default configuration cannot be disabled. Select another default first.");
        }
        if (!config.isEnabled()) {
            return response(config);
        }

        config.disable();
        AiProviderConfig saved = repository.saveAndFlush(config);
        audit(actorId, actorEmail, AuditEventAction.AI_PROVIDER_CONFIG_DISABLED, saved);
        return response(saved);
    }

    @Override
    @Transactional
    public AiProviderConfigResponse makeDefault(
            UUID actorId, String actorEmail, UUID configId, long expectedVersion) {
        AiProviderConfig config = requireActiveForUpdate(configId);
        requireVersion(config, expectedVersion);
        requireProviderReady(config.getProvider());
        if (!config.isEnabled()) {
            config.enable();
        }
        if (config.isDefaultProvider()) {
            return response(config);
        }

        repository.findDefaultByPurposeForUpdate(config.getPurpose())
                .ifPresent(this::clearAndFlushDefault);
        config.makeDefault();
        AiProviderConfig saved = repository.saveAndFlush(config);
        audit(
                actorId,
                actorEmail,
                AuditEventAction.AI_PROVIDER_CONFIG_DEFAULT_CHANGED,
                saved);
        return response(saved);
    }

    @Override
    @Transactional
    public void archive(
            UUID actorId, String actorEmail, UUID configId, long expectedVersion) {
        AiProviderConfig config = requireActiveForUpdate(configId);
        requireVersion(config, expectedVersion);
        if (config.isDefaultProvider()) {
            throw defaultRequired(
                    "The default configuration cannot be archived. Select another default first.");
        }

        config.archive(Instant.now());
        repository.saveAndFlush(config);
        audit(actorId, actorEmail, AuditEventAction.AI_PROVIDER_CONFIG_ARCHIVED, config);
    }

    @Override
    @Transactional
    public AiProviderConnectionTestResponse testConnection(
            UUID actorId, String actorEmail, UUID configId) {
        AiProviderConfig config = requireActive(configId);
        AiProvider provider = config.getProvider();
        Instant testedAt = Instant.now();

        ResolvedAiCredential resolved = null;
        ProviderConnectionResult result;
        if (!provider.isEnabled()) {
            result = ProviderConnectionResult.failure(
                    0L, "PROVIDER_DISABLED", "The selected provider is disabled.");
        } else {
            Optional<ResolvedAiCredential> selected =
                    credentialSelector.findFirstAvailable(provider.getId());
            if (selected.isEmpty()) {
                result = ProviderConnectionResult.failure(
                        0L,
                        "CREDENTIAL_UNAVAILABLE",
                        "No enabled provider credential could be resolved.");
            } else {
                resolved = selected.get();
                result = connectionGateway.test(provider, config, resolved.secret());
            }
        }

        audit(actorId, actorEmail, AuditEventAction.AI_PROVIDER_CONNECTION_TESTED, config);
        return new AiProviderConnectionTestResponse(
                config.getId(),
                provider.getId(),
                provider.getCode(),
                config.getPurpose(),
                config.getModel(),
                resolved == null ? null : resolved.credential().getId(),
                resolved == null ? null : resolved.credential().getLabel(),
                result.success(),
                result.latencyMs(),
                result.failureCategory(),
                result.message(),
                testedAt);
    }

    @Override
    @Transactional(readOnly = true)
    public AiProviderConfig requireDefault(AiPurpose purpose) {
        AiProviderConfig config = repository
                .findByPurposeAndDefaultProviderTrueAndArchivedAtIsNull(purpose)
                .filter(AiProviderConfig::isEnabled)
                .filter(item -> item.getProvider().isEnabled())
                .orElseThrow(() -> unavailable(
                        "No default AI provider is available for " + purpose + "."));
        if (credentialSelector.findFirstAvailable(config.getProvider().getId()).isEmpty()) {
            throw unavailable("The default AI provider credential is unavailable.");
        }
        return config;
    }

    private AiProvider requireProvider(UUID providerId) {
        return providerRepository.findByIdAndArchivedAtIsNull(providerId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.AI_PROVIDER_NOT_FOUND,
                        "AI provider was not found."));
    }

    private AiProviderConfig requireActive(UUID configId) {
        return repository.findByIdAndArchivedAtIsNull(configId)
                .orElseThrow(this::notFound);
    }

    private AiProviderConfig requireActiveForUpdate(UUID configId) {
        return repository.findActiveByIdForUpdate(configId)
                .orElseThrow(this::notFound);
    }

    private void requireVersion(AiProviderConfig config, long expectedVersion) {
        if (config.getVersion() != expectedVersion) {
            throw new BusinessException(
                    ErrorCode.CONCURRENT_MODIFICATION,
                    "The provider configuration was modified. Reload it and try again.");
        }
    }

    private void requireProviderReady(AiProvider provider) {
        if (!provider.isEnabled()) {
            throw invalid("The provider must be enabled before it can become a default.");
        }
        if (credentialRepository
                        .countByProviderIdAndEnabledTrueAndArchivedAtIsNull(provider.getId())
                == 0) {
            throw new BusinessException(
                    ErrorCode.AI_PROVIDER_CREDENTIAL_REQUIRED,
                    "The provider needs an enabled credential before it can become a default.");
        }
    }

    private void validateDefaultState(boolean enabled, boolean defaultProvider) {
        if (defaultProvider && !enabled) {
            throw invalid("A default provider configuration must be enabled.");
        }
    }

    private void clearAndFlushDefault(AiProviderConfig currentDefault) {
        currentDefault.clearDefault();
        repository.saveAndFlush(currentDefault);
    }

    private AiProviderConfigResponse response(AiProviderConfig config) {
        AiProvider provider = config.getProvider();
        return new AiProviderConfigResponse(
                config.getId(),
                config.getVersion(),
                provider.getId(),
                provider.getCode(),
                provider.getDisplayName(),
                provider.getProtocol(),
                provider.getBaseUrl(),
                config.getPurpose(),
                config.getModel(),
                config.isEnabled(),
                config.isDefaultProvider(),
                config.getTimeoutSeconds(),
                config.getMaxInputTokens(),
                config.getMaxOutputTokens(),
                config.getTemperature(),
                config.getCreatedAt(),
                config.getUpdatedAt());
    }

    private void audit(
            UUID actorId,
            String actorEmail,
            AuditEventAction action,
            AiProviderConfig config) {
        auditLogService.logAction(
                actorId,
                actorEmail,
                action,
                TARGET_RESOURCE,
                config.getId().toString());
    }

    private BusinessException notFound() {
        return new BusinessException(
                ErrorCode.AI_PROVIDER_CONFIG_NOT_FOUND,
                "AI provider configuration was not found.");
    }

    private BusinessException defaultRequired(String message) {
        return new BusinessException(ErrorCode.AI_PROVIDER_DEFAULT_REQUIRED, message);
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.AI_PROVIDER_INVALID_CONFIGURATION, message);
    }

    private BusinessException unavailable(String message) {
        return new BusinessException(ErrorCode.AI_PROVIDER_UNAVAILABLE, message);
    }
}
