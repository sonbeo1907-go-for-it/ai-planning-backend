package com.codegym.aiplanning.service.ai.impl;

import com.codegym.aiplanning.entity.ai.AiProviderCredential;
import com.codegym.aiplanning.repository.ai.AiProviderCredentialRepository;
import com.codegym.aiplanning.service.ai.AiCredentialSelector;
import com.codegym.aiplanning.service.ai.ResolvedAiCredential;
import com.codegym.aiplanning.service.ai.secret.AiSecretStore;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PriorityAiCredentialSelector implements AiCredentialSelector {

    private final AiProviderCredentialRepository repository;
    private final AiSecretStore secretStore;

    public PriorityAiCredentialSelector(
            AiProviderCredentialRepository repository, AiSecretStore secretStore) {
        this.repository = repository;
        this.secretStore = secretStore;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ResolvedAiCredential> findFirstAvailable(UUID providerId) {
        for (AiProviderCredential credential : repository
                .findAllByProviderIdAndEnabledTrueAndArchivedAtIsNullOrderByPriorityDescCreatedAtAsc(
                        providerId)) {
            Optional<String> secret = secretStore.resolve(credential.getSecretRef());
            if (secret.isPresent()) {
                return Optional.of(new ResolvedAiCredential(credential, secret.get()));
            }
        }
        return Optional.empty();
    }
}
