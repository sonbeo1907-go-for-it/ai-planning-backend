package com.codegym.aiplanning.service.ai.prompt;

import com.codegym.aiplanning.controller.admin.ai.dto.AiPromptPreviewRequest;
import com.codegym.aiplanning.controller.admin.ai.dto.AiPromptPreviewResponse;
import com.codegym.aiplanning.controller.admin.ai.dto.AiPromptResponse;
import com.codegym.aiplanning.controller.admin.ai.dto.CreateAiPromptDraftRequest;
import com.codegym.aiplanning.controller.admin.ai.dto.UpdateAiPromptDraftRequest;
import com.codegym.aiplanning.entity.ai.AiPurpose;
import java.util.List;
import java.util.UUID;

public interface AdminAiPromptService {

    AiPromptResponse createDraft(UUID actorId, String actorEmail, CreateAiPromptDraftRequest request);

    AiPromptResponse updateDraft(UUID actorId, String actorEmail, UUID promptId, UpdateAiPromptDraftRequest request);

    AiPromptResponse publish(UUID actorId, String actorEmail, UUID promptId, long expectedVersion);

    AiPromptResponse activate(UUID actorId, String actorEmail, UUID promptId, long expectedVersion);

    AiPromptResponse rollback(UUID actorId, String actorEmail, UUID promptId, long expectedVersion);

    void archive(UUID actorId, String actorEmail, UUID promptId, long expectedVersion);

    AiPromptPreviewResponse preview(AiPromptPreviewRequest request);

    List<AiPromptResponse> listByPurpose(AiPurpose purpose);

    AiPromptResponse getById(UUID promptId);

    com.codegym.aiplanning.controller.admin.ai.dto.AiPromptDefaultResponse getDefaultPrompt(AiPurpose purpose);
}
