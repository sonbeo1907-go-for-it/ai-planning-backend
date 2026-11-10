package com.codegym.aiplanning.controller.admin.ai;

import com.codegym.aiplanning.common.api.ApiResponse;
import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.controller.admin.ai.dto.AiProviderActionRequest;
import com.codegym.aiplanning.controller.admin.ai.dto.AiProviderCredentialResponse;
import com.codegym.aiplanning.controller.admin.ai.dto.AiProviderResponse;
import com.codegym.aiplanning.controller.admin.ai.dto.CreateAiProviderCredentialRequest;
import com.codegym.aiplanning.controller.admin.ai.dto.CreateAiProviderRequest;
import com.codegym.aiplanning.controller.admin.ai.dto.UpdateAiProviderCredentialRequest;
import com.codegym.aiplanning.controller.admin.ai.dto.UpdateAiProviderRequest;
import com.codegym.aiplanning.service.ai.AiProviderAdminService;
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
@RequestMapping(ApiConstant.ADMIN_AI_PROVIDERS)
@PreAuthorize("hasRole('ADMIN')")
@Tag(
        name = "AI Provider Registry",
        description = "ADMIN-only provider identity, protocol, and credential metadata management")
public class AiProviderController {

    private final AiProviderAdminService service;

    public AiProviderController(AiProviderAdminService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Register an AI provider",
            description = "Provider code is immutable. Only a secret reference may be supplied; raw keys are rejected by contract.")
    public ApiResponse<AiProviderResponse> create(
            @Valid @RequestBody CreateAiProviderRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.create(actorId(jwt), actorEmail(jwt), request));
    }

    @GetMapping
    @Operation(summary = "List active AI providers and their credential metadata")
    public ApiResponse<List<AiProviderResponse>> list() {
        return ApiResponse.of(service.list());
    }

    @GetMapping(ApiConstant.AI_PROVIDER_BY_ID)
    @Operation(summary = "Get one active AI provider")
    public ApiResponse<AiProviderResponse> get(@PathVariable UUID providerId) {
        return ApiResponse.of(service.get(providerId));
    }

    @PutMapping(ApiConstant.AI_PROVIDER_BY_ID)
    @Operation(summary = "Update provider display, endpoint, protocol, and credential strategy")
    public ApiResponse<AiProviderResponse> update(
            @PathVariable UUID providerId,
            @Valid @RequestBody UpdateAiProviderRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(
                service.update(actorId(jwt), actorEmail(jwt), providerId, request));
    }

    @PostMapping(ApiConstant.AI_PROVIDER_ENABLE)
    @Operation(summary = "Enable a provider")
    public ApiResponse<AiProviderResponse> enable(
            @PathVariable UUID providerId,
            @Valid @RequestBody AiProviderActionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(
                service.enable(actorId(jwt), actorEmail(jwt), providerId, request));
    }

    @PostMapping(ApiConstant.AI_PROVIDER_DISABLE)
    @Operation(summary = "Disable a provider not used by a default configuration")
    public ApiResponse<AiProviderResponse> disable(
            @PathVariable UUID providerId,
            @Valid @RequestBody AiProviderActionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(
                service.disable(actorId(jwt), actorEmail(jwt), providerId, request));
    }

    @DeleteMapping(ApiConstant.AI_PROVIDER_BY_ID)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Archive a provider after its active configurations are archived")
    public void archive(
            @PathVariable UUID providerId,
            @RequestParam @Min(0) long version,
            @AuthenticationPrincipal Jwt jwt) {
        service.archive(actorId(jwt), actorEmail(jwt), providerId, version);
    }

    @GetMapping(ApiConstant.AI_PROVIDER_CREDENTIALS)
    @Operation(summary = "List active credential metadata for a provider")
    public ApiResponse<List<AiProviderCredentialResponse>> listCredentials(
            @PathVariable UUID providerId) {
        return ApiResponse.of(service.listCredentials(providerId));
    }

    @PostMapping(ApiConstant.AI_PROVIDER_CREDENTIALS)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a secret reference to a provider")
    public ApiResponse<AiProviderCredentialResponse> createCredential(
            @PathVariable UUID providerId,
            @Valid @RequestBody CreateAiProviderCredentialRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.createCredential(
                actorId(jwt), actorEmail(jwt), providerId, request));
    }

    @PutMapping(ApiConstant.AI_PROVIDER_CREDENTIAL_BY_ID)
    @Operation(summary = "Update credential metadata and priority")
    public ApiResponse<AiProviderCredentialResponse> updateCredential(
            @PathVariable UUID providerId,
            @PathVariable UUID credentialId,
            @Valid @RequestBody UpdateAiProviderCredentialRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.updateCredential(
                actorId(jwt), actorEmail(jwt), providerId, credentialId, request));
    }

    @PostMapping(ApiConstant.AI_PROVIDER_CREDENTIAL_ENABLE)
    @Operation(summary = "Enable a provider credential")
    public ApiResponse<AiProviderCredentialResponse> enableCredential(
            @PathVariable UUID providerId,
            @PathVariable UUID credentialId,
            @Valid @RequestBody AiProviderActionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.enableCredential(
                actorId(jwt), actorEmail(jwt), providerId, credentialId, request));
    }

    @PostMapping(ApiConstant.AI_PROVIDER_CREDENTIAL_DISABLE)
    @Operation(summary = "Disable a provider credential")
    public ApiResponse<AiProviderCredentialResponse> disableCredential(
            @PathVariable UUID providerId,
            @PathVariable UUID credentialId,
            @Valid @RequestBody AiProviderActionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.disableCredential(
                actorId(jwt), actorEmail(jwt), providerId, credentialId, request));
    }

    @DeleteMapping(ApiConstant.AI_PROVIDER_CREDENTIAL_BY_ID)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Archive provider credential metadata")
    public void archiveCredential(
            @PathVariable UUID providerId,
            @PathVariable UUID credentialId,
            @RequestParam @Min(0) long version,
            @AuthenticationPrincipal Jwt jwt) {
        service.archiveCredential(
                actorId(jwt), actorEmail(jwt), providerId, credentialId, version);
    }

    private UUID actorId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private String actorEmail(Jwt jwt) {
        return jwt.getClaimAsString("email");
    }
}
