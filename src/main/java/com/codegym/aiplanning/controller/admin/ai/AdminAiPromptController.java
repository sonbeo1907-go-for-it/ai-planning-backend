package com.codegym.aiplanning.controller.admin.ai;

import com.codegym.aiplanning.common.api.ApiResponse;
import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.controller.admin.ai.dto.AiPromptPreviewRequest;
import com.codegym.aiplanning.controller.admin.ai.dto.AiPromptPreviewResponse;
import com.codegym.aiplanning.controller.admin.ai.dto.AiPromptResponse;
import com.codegym.aiplanning.controller.admin.ai.dto.CreateAiPromptDraftRequest;
import com.codegym.aiplanning.controller.admin.ai.dto.UpdateAiPromptDraftRequest;
import com.codegym.aiplanning.entity.ai.AiPurpose;
import com.codegym.aiplanning.service.ai.prompt.AdminAiPromptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstant.ADMIN_AI_PROMPTS)
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "AI Prompt Management", description = "ADMIN-only system prompt versioning, publishing, activation, and rollback")
public class AdminAiPromptController {

    private final AdminAiPromptService service;

    public AdminAiPromptController(AdminAiPromptService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a draft system prompt template")
    public ApiResponse<AiPromptResponse> createDraft(
            @Valid @RequestBody CreateAiPromptDraftRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.createDraft(actorId(jwt), actorEmail(jwt), request));
    }

    @PutMapping(ApiConstant.AI_PROMPT_BY_ID)
    @Operation(summary = "Update a draft system prompt template")
    public ApiResponse<AiPromptResponse> updateDraft(
            @PathVariable UUID promptId,
            @Valid @RequestBody UpdateAiPromptDraftRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.updateDraft(actorId(jwt), actorEmail(jwt), promptId, request));
    }

    @PostMapping(ApiConstant.AI_PROMPT_PUBLISH)
    @Operation(summary = "Publish a draft prompt template to immutable state")
    public ApiResponse<AiPromptResponse> publish(
            @PathVariable UUID promptId,
            @RequestParam @Min(0) long version,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.publish(actorId(jwt), actorEmail(jwt), promptId, version));
    }

    @PostMapping(ApiConstant.AI_PROMPT_ACTIVATE)
    @Operation(summary = "Activate a published prompt template")
    public ApiResponse<AiPromptResponse> activate(
            @PathVariable UUID promptId,
            @RequestParam @Min(0) long version,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.activate(actorId(jwt), actorEmail(jwt), promptId, version));
    }

    @PostMapping(ApiConstant.AI_PROMPT_ROLLBACK)
    @Operation(summary = "Rollback to a previously published prompt template")
    public ApiResponse<AiPromptResponse> rollback(
            @PathVariable UUID promptId,
            @RequestParam @Min(0) long version,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.rollback(actorId(jwt), actorEmail(jwt), promptId, version));
    }

    @DeleteMapping(ApiConstant.AI_PROMPT_BY_ID)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Archive a prompt template")
    public void archive(
            @PathVariable UUID promptId,
            @RequestParam @Min(0) long version,
            @AuthenticationPrincipal Jwt jwt) {
        service.archive(actorId(jwt), actorEmail(jwt), promptId, version);
    }

    @PostMapping(ApiConstant.AI_PROMPT_PREVIEW)
    @Operation(summary = "Preview rendered prompt with synthetic data without calling external provider")
    public ApiResponse<AiPromptPreviewResponse> preview(
            @Valid @RequestBody AiPromptPreviewRequest request) {
        return ApiResponse.of(service.preview(request));
    }

    @GetMapping
    @Operation(summary = "List prompt templates for a purpose")
    public ApiResponse<List<AiPromptResponse>> listByPurpose(
            @RequestParam AiPurpose purpose) {
        return ApiResponse.of(service.listByPurpose(purpose));
    }

    @GetMapping(ApiConstant.AI_PROMPT_BY_ID)
    @Operation(summary = "Get a prompt template by ID")
    public ApiResponse<AiPromptResponse> getById(
            @PathVariable UUID promptId) {
        return ApiResponse.of(service.getById(promptId));
    }

    @GetMapping("/default")
    @Operation(summary = "Get the latest system default prompt for a purpose")
    public ApiResponse<com.codegym.aiplanning.controller.admin.ai.dto.AiPromptDefaultResponse> getDefault(
            @RequestParam AiPurpose purpose) {
        return ApiResponse.of(service.getDefaultPrompt(purpose));
    }

    private UUID actorId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private String actorEmail(Jwt jwt) {
        return jwt.getClaimAsString("email");
    }
}
