package com.codegym.aiplanning.service.ai;

import com.codegym.aiplanning.controller.admin.ai.dto.AiProviderConfigResponse;
import com.codegym.aiplanning.controller.admin.ai.dto.AiProviderConnectionTestResponse;
import com.codegym.aiplanning.controller.admin.ai.dto.CreateAiProviderConfigRequest;
import com.codegym.aiplanning.controller.admin.ai.dto.UpdateAiProviderConfigRequest;
import java.util.List;
import java.util.UUID;

public interface AiProviderConfigService {

    AiProviderConfigResponse create(
            UUID actorId, String actorEmail, CreateAiProviderConfigRequest request);

    List<AiProviderConfigResponse> list();

    AiProviderConfigResponse get(UUID configId);

    AiProviderConfigResponse update(
            UUID actorId,
            String actorEmail,
            UUID configId,
            UpdateAiProviderConfigRequest request);

    AiProviderConfigResponse enable(
            UUID actorId, String actorEmail, UUID configId, long expectedVersion);

    AiProviderConfigResponse disable(
            UUID actorId, String actorEmail, UUID configId, long expectedVersion);

    AiProviderConfigResponse makeDefault(
            UUID actorId, String actorEmail, UUID configId, long expectedVersion);

    void archive(
            UUID actorId, String actorEmail, UUID configId, long expectedVersion);

    AiProviderConnectionTestResponse testConnection(
            UUID actorId, String actorEmail, UUID configId);
}
