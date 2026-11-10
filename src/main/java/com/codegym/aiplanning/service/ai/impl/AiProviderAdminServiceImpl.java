package com.codegym.aiplanning.service.ai.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.common.utils.StringMaskUtils;
import com.codegym.aiplanning.controller.admin.ai.dto.AiProviderActionRequest;
import com.codegym.aiplanning.controller.admin.ai.dto.AiProviderCredentialResponse;
import com.codegym.aiplanning.controller.admin.ai.dto.AiProviderResponse;
import com.codegym.aiplanning.controller.admin.ai.dto.CreateAiProviderCredentialRequest;
import com.codegym.aiplanning.controller.admin.ai.dto.CreateAiProviderRequest;
import com.codegym.aiplanning.controller.admin.ai.dto.InitialAiProviderCredentialRequest;
import com.codegym.aiplanning.controller.admin.ai.dto.UpdateAiProviderCredentialRequest;
import com.codegym.aiplanning.controller.admin.ai.dto.UpdateAiProviderRequest;
import com.codegym.aiplanning.entity.ai.AiProvider;
import com.codegym.aiplanning.entity.ai.AiProviderCredential;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.repository.ai.AiProviderConfigRepository;
import com.codegym.aiplanning.repository.ai.AiProviderCredentialRepository;
import com.codegym.aiplanning.repository.ai.AiProviderRepository;
import com.codegym.aiplanning.service.ai.AiProviderAdminService;
import com.codegym.aiplanning.service.ai.secret.AiSecretStore;
import com.codegym.aiplanning.service.audit.AuditLogService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiProviderAdminServiceImpl implements AiProviderAdminService {

    private static final String PROVIDER_RESOURCE = "AiProvider";
    private static final String CREDENTIAL_RESOURCE = "AiProviderCredential";

    private final AiProviderRepository providerRepository;
    private final AiProviderCredentialRepository credentialRepository;
    private final AiProviderConfigRepository configRepository;
    private final AiProviderConfigurationPolicy policy;
    private final AiSecretStore secretStore;
    private final AuditLogService auditLogService;

    public AiProviderAdminServiceImpl(
            AiProviderRepository providerRepository,
            AiProviderCredentialRepository credentialRepository,
            AiProviderConfigRepository configRepository,
            AiProviderConfigurationPolicy policy,
            AiSecretStore secretStore,
            AuditLogService auditLogService) {
        this.providerRepository = providerRepository;
        this.credentialRepository = credentialRepository;
        this.configRepository = configRepository;
        this.policy = policy;
        this.secretStore = secretStore;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    public AiProviderResponse create(
            UUID actorId, String actorEmail, CreateAiProviderRequest request) {
        String code = policy.normalizeProviderCode(request.code());
        if (providerRepository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException(
                    ErrorCode.AI_PROVIDER_CODE_EXISTS,
                    "An AI provider with this code already exists.");
        }

        AiProvider provider = AiProvider.create(
                code,
                policy.normalizeDisplayName(request.displayName()),
                policy.validateAndNormalizeBaseUrl(request.baseUrl()),
                request.protocol(),
                request.credentialStrategy(),
                request.enabled());
        AiProvider saved = providerRepository.saveAndFlush(provider);

        List<AiProviderCredential> credentials = new ArrayList<>();
        if (request.initialCredential() != null) {
            AiProviderCredential credential =
                    createInitialCredential(saved, request.initialCredential());
            credentials.add(credential);
            audit(
                    actorId,
                    actorEmail,
                    AuditEventAction.AI_PROVIDER_CREDENTIAL_CREATED,
                    CREDENTIAL_RESOURCE,
                    credential.getId());
        }

        audit(
                actorId,
                actorEmail,
                AuditEventAction.AI_PROVIDER_CREATED,
                PROVIDER_RESOURCE,
                saved.getId());
        return response(saved, credentials);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiProviderResponse> list() {
        Map<UUID, List<AiProviderCredential>> credentialsByProvider = new HashMap<>();
        credentialRepository.findAllByArchivedAtIsNullOrderByProviderCodeAscPriorityDesc()
                .forEach(credential -> credentialsByProvider
                        .computeIfAbsent(credential.getProvider().getId(), ignored -> new ArrayList<>())
                        .add(credential));

        return providerRepository.findAllByArchivedAtIsNullOrderByCodeAsc().stream()
                .map(provider -> response(
                        provider,
                        credentialsByProvider.getOrDefault(provider.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AiProviderResponse get(UUID providerId) {
        AiProvider provider = requireActive(providerId);
        return response(provider, activeCredentials(providerId));
    }

    @Override
    @Transactional
    public AiProviderResponse update(
            UUID actorId,
            String actorEmail,
            UUID providerId,
            UpdateAiProviderRequest request) {
        AiProvider provider = requireActiveForUpdate(providerId);
        requireVersion(provider.getVersion(), request.version());
        provider.update(
                policy.normalizeDisplayName(request.displayName()),
                policy.validateAndNormalizeBaseUrl(request.baseUrl()),
                request.protocol(),
                request.credentialStrategy());
        AiProvider saved = providerRepository.saveAndFlush(provider);
        audit(
                actorId,
                actorEmail,
                AuditEventAction.AI_PROVIDER_UPDATED,
                PROVIDER_RESOURCE,
                saved.getId());
        return response(saved, activeCredentials(providerId));
    }

    @Override
    @Transactional
    public AiProviderResponse enable(
            UUID actorId,
            String actorEmail,
            UUID providerId,
            AiProviderActionRequest request) {
        AiProvider provider = requireActiveForUpdate(providerId);
        requireVersion(provider.getVersion(), request.version());
        if (!provider.isEnabled()) {
            provider.enable();
            providerRepository.saveAndFlush(provider);
            audit(
                    actorId,
                    actorEmail,
                    AuditEventAction.AI_PROVIDER_ENABLED,
                    PROVIDER_RESOURCE,
                    provider.getId());
        }
        return response(provider, activeCredentials(providerId));
    }

    @Override
    @Transactional
    public AiProviderResponse disable(
            UUID actorId,
            String actorEmail,
            UUID providerId,
            AiProviderActionRequest request) {
        AiProvider provider = requireActiveForUpdate(providerId);
        requireVersion(provider.getVersion(), request.version());
        if (configRepository
                .existsByProviderIdAndDefaultProviderTrueAndArchivedAtIsNull(providerId)) {
            throw new BusinessException(
                    ErrorCode.AI_PROVIDER_IN_USE,
                    "A provider used by a default configuration cannot be disabled.");
        }
        if (provider.isEnabled()) {
            provider.disable();
            providerRepository.saveAndFlush(provider);
            audit(
                    actorId,
                    actorEmail,
                    AuditEventAction.AI_PROVIDER_DISABLED,
                    PROVIDER_RESOURCE,
                    provider.getId());
        }
        return response(provider, activeCredentials(providerId));
    }

    @Override
    @Transactional
    public void archive(
            UUID actorId, String actorEmail, UUID providerId, long expectedVersion) {
        AiProvider provider = requireActiveForUpdate(providerId);
        requireVersion(provider.getVersion(), expectedVersion);
        if (configRepository.existsByProviderIdAndArchivedAtIsNull(providerId)) {
            throw new BusinessException(
                    ErrorCode.AI_PROVIDER_IN_USE,
                    "Archive all active configurations for this provider first.");
        }

        Instant archivedAt = Instant.now();
        activeCredentials(providerId).forEach(credential -> credential.archive(archivedAt));
        credentialRepository.flush();
        provider.archive(archivedAt);
        providerRepository.saveAndFlush(provider);
        audit(
                actorId,
                actorEmail,
                AuditEventAction.AI_PROVIDER_ARCHIVED,
                PROVIDER_RESOURCE,
                provider.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiProviderCredentialResponse> listCredentials(UUID providerId) {
        requireActive(providerId);
        return activeCredentials(providerId).stream().map(this::credentialResponse).toList();
    }

    @Override
    @Transactional
    public AiProviderCredentialResponse createCredential(
            UUID actorId,
            String actorEmail,
            UUID providerId,
            CreateAiProviderCredentialRequest request) {
        AiProvider provider = requireActiveForUpdate(providerId);
        AiProviderCredential credential = AiProviderCredential.create(
                provider,
                policy.normalizeCredentialLabel(request.label()),
                policy.normalizeSecretRef(request.secretRef()),
                request.priority(),
                request.enabled());
        AiProviderCredential saved = credentialRepository.saveAndFlush(credential);
        audit(
                actorId,
                actorEmail,
                AuditEventAction.AI_PROVIDER_CREDENTIAL_CREATED,
                CREDENTIAL_RESOURCE,
                saved.getId());
        return credentialResponse(saved);
    }

    @Override
    @Transactional
    public AiProviderCredentialResponse updateCredential(
            UUID actorId,
            String actorEmail,
            UUID providerId,
            UUID credentialId,
            UpdateAiProviderCredentialRequest request) {
        AiProviderCredential credential = requireCredentialForUpdate(providerId, credentialId);
        requireVersion(credential.getVersion(), request.version());
        credential.update(
                policy.normalizeCredentialLabel(request.label()),
                policy.normalizeSecretRef(request.secretRef()),
                request.priority());
        AiProviderCredential saved = credentialRepository.saveAndFlush(credential);
        audit(
                actorId,
                actorEmail,
                AuditEventAction.AI_PROVIDER_CREDENTIAL_UPDATED,
                CREDENTIAL_RESOURCE,
                saved.getId());
        return credentialResponse(saved);
    }

    @Override
    @Transactional
    public AiProviderCredentialResponse enableCredential(
            UUID actorId,
            String actorEmail,
            UUID providerId,
            UUID credentialId,
            AiProviderActionRequest request) {
        AiProviderCredential credential = requireCredentialForUpdate(providerId, credentialId);
        requireVersion(credential.getVersion(), request.version());
        if (!credential.isEnabled()) {
            credential.enable();
            credentialRepository.saveAndFlush(credential);
            audit(
                    actorId,
                    actorEmail,
                    AuditEventAction.AI_PROVIDER_CREDENTIAL_ENABLED,
                    CREDENTIAL_RESOURCE,
                    credential.getId());
        }
        return credentialResponse(credential);
    }

    @Override
    @Transactional
    public AiProviderCredentialResponse disableCredential(
            UUID actorId,
            String actorEmail,
            UUID providerId,
            UUID credentialId,
            AiProviderActionRequest request) {
        AiProviderCredential credential = requireCredentialForUpdate(providerId, credentialId);
        requireVersion(credential.getVersion(), request.version());
        requireCredentialCanBecomeUnavailable(providerId, credential);
        if (credential.isEnabled()) {
            credential.disable();
            credentialRepository.saveAndFlush(credential);
            audit(
                    actorId,
                    actorEmail,
                    AuditEventAction.AI_PROVIDER_CREDENTIAL_DISABLED,
                    CREDENTIAL_RESOURCE,
                    credential.getId());
        }
        return credentialResponse(credential);
    }

    @Override
    @Transactional
    public void archiveCredential(
            UUID actorId,
            String actorEmail,
            UUID providerId,
            UUID credentialId,
            long expectedVersion) {
        AiProviderCredential credential = requireCredentialForUpdate(providerId, credentialId);
        requireVersion(credential.getVersion(), expectedVersion);
        requireCredentialCanBecomeUnavailable(providerId, credential);
        credential.archive(Instant.now());
        credentialRepository.saveAndFlush(credential);
        audit(
                actorId,
                actorEmail,
                AuditEventAction.AI_PROVIDER_CREDENTIAL_ARCHIVED,
                CREDENTIAL_RESOURCE,
                credential.getId());
    }

    private AiProviderCredential createInitialCredential(
            AiProvider provider, InitialAiProviderCredentialRequest request) {
        AiProviderCredential credential = AiProviderCredential.create(
                provider,
                policy.normalizeCredentialLabel(request.label()),
                policy.normalizeSecretRef(request.secretRef()),
                request.priority(),
                request.enabled());
        return credentialRepository.saveAndFlush(credential);
    }

    private void requireCredentialCanBecomeUnavailable(
            UUID providerId, AiProviderCredential credential) {
        if (!credential.isEnabled()) {
            return;
        }
        boolean providerIsDefault = configRepository
                .existsByProviderIdAndDefaultProviderTrueAndArchivedAtIsNull(providerId);
        long enabledCredentials = credentialRepository
                .countByProviderIdAndEnabledTrueAndArchivedAtIsNull(providerId);
        if (providerIsDefault && enabledCredentials <= 1) {
            throw new BusinessException(
                    ErrorCode.AI_PROVIDER_CREDENTIAL_REQUIRED,
                    "The last enabled credential of a default provider cannot be disabled or archived.");
        }
    }

    private List<AiProviderCredential> activeCredentials(UUID providerId) {
        return credentialRepository
                .findAllByProviderIdAndArchivedAtIsNullOrderByPriorityDescCreatedAtAsc(providerId);
    }

    private AiProvider requireActive(UUID providerId) {
        return providerRepository.findByIdAndArchivedAtIsNull(providerId)
                .orElseThrow(this::providerNotFound);
    }

    private AiProvider requireActiveForUpdate(UUID providerId) {
        return providerRepository.findActiveByIdForUpdate(providerId)
                .orElseThrow(this::providerNotFound);
    }

    private AiProviderCredential requireCredentialForUpdate(
            UUID providerId, UUID credentialId) {
        return credentialRepository.findActiveByIdForUpdate(providerId, credentialId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.AI_PROVIDER_CREDENTIAL_NOT_FOUND,
                        "AI provider credential was not found."));
    }

    private void requireVersion(long actualVersion, long expectedVersion) {
        if (actualVersion != expectedVersion) {
            throw new BusinessException(
                    ErrorCode.CONCURRENT_MODIFICATION,
                    "The resource was modified. Reload it and try again.");
        }
    }

    private AiProviderResponse response(
            AiProvider provider, List<AiProviderCredential> credentials) {
        return new AiProviderResponse(
                provider.getId(),
                provider.getVersion(),
                provider.getCode(),
                provider.getDisplayName(),
                provider.getBaseUrl(),
                provider.getProtocol(),
                provider.getCredentialStrategy(),
                provider.isEnabled(),
                credentials.stream().map(this::credentialResponse).toList(),
                provider.getCreatedAt(),
                provider.getUpdatedAt());
    }

    private AiProviderCredentialResponse credentialResponse(
            AiProviderCredential credential) {
        Optional<String> secret = secretStore.resolve(credential.getSecretRef());
        return new AiProviderCredentialResponse(
                credential.getId(),
                credential.getVersion(),
                credential.getProvider().getId(),
                credential.getLabel(),
                credential.getSecretRef(),
                credential.getPriority(),
                credential.isEnabled(),
                secret.isPresent(),
                secret.map(StringMaskUtils::maskSecret).orElse(null),
                credential.getCreatedAt(),
                credential.getUpdatedAt());
    }

    private void audit(
            UUID actorId,
            String actorEmail,
            AuditEventAction action,
            String resourceType,
            UUID resourceId) {
        auditLogService.logAction(
                actorId, actorEmail, action, resourceType, resourceId.toString());
    }

    private BusinessException providerNotFound() {
        return new BusinessException(
                ErrorCode.AI_PROVIDER_NOT_FOUND, "AI provider was not found.");
    }
}
