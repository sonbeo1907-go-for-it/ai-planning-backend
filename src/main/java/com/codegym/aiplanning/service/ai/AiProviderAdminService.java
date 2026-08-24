package com.codegym.aiplanning.service.ai;

import com.codegym.aiplanning.controller.admin.ai.dto.AiProviderActionRequest;
import com.codegym.aiplanning.controller.admin.ai.dto.AiProviderCredentialResponse;
import com.codegym.aiplanning.controller.admin.ai.dto.AiProviderResponse;
import com.codegym.aiplanning.controller.admin.ai.dto.CreateAiProviderCredentialRequest;
import com.codegym.aiplanning.controller.admin.ai.dto.CreateAiProviderRequest;
import com.codegym.aiplanning.controller.admin.ai.dto.UpdateAiProviderCredentialRequest;
import com.codegym.aiplanning.controller.admin.ai.dto.UpdateAiProviderRequest;
import java.util.List;
import java.util.UUID;

public interface AiProviderAdminService {

    AiProviderResponse create(
            UUID actorId, String actorEmail, CreateAiProviderRequest request);

    List<AiProviderResponse> list();

    AiProviderResponse get(UUID providerId);

    AiProviderResponse update(
            UUID actorId,
            String actorEmail,
            UUID providerId,
            UpdateAiProviderRequest request);

    AiProviderResponse enable(
            UUID actorId, String actorEmail, UUID providerId, AiProviderActionRequest request);

    AiProviderResponse disable(
            UUID actorId, String actorEmail, UUID providerId, AiProviderActionRequest request);

    void archive(
            UUID actorId, String actorEmail, UUID providerId, long expectedVersion);

    List<AiProviderCredentialResponse> listCredentials(UUID providerId);

    AiProviderCredentialResponse createCredential(
            UUID actorId,
            String actorEmail,
            UUID providerId,
            CreateAiProviderCredentialRequest request);

    AiProviderCredentialResponse updateCredential(
            UUID actorId,
            String actorEmail,
            UUID providerId,
            UUID credentialId,
            UpdateAiProviderCredentialRequest request);

    AiProviderCredentialResponse enableCredential(
            UUID actorId,
            String actorEmail,
            UUID providerId,
            UUID credentialId,
            AiProviderActionRequest request);

    AiProviderCredentialResponse disableCredential(
            UUID actorId,
            String actorEmail,
            UUID providerId,
            UUID credentialId,
            AiProviderActionRequest request);

    void archiveCredential(
            UUID actorId,
            String actorEmail,
            UUID providerId,
            UUID credentialId,
            long expectedVersion);
}
