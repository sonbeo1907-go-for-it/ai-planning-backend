package com.codegym.aiplanning.controller.admin.ai;

import com.codegym.aiplanning.common.api.ApiError;
import com.codegym.aiplanning.common.api.ApiResponse;
import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.controller.admin.ai.dto.AiProviderActionRequest;
import com.codegym.aiplanning.controller.admin.ai.dto.AiProviderConfigResponse;
import com.codegym.aiplanning.controller.admin.ai.dto.AiProviderConnectionTestResponse;
import com.codegym.aiplanning.controller.admin.ai.dto.CreateAiProviderConfigRequest;
import com.codegym.aiplanning.controller.admin.ai.dto.UpdateAiProviderConfigRequest;
import com.codegym.aiplanning.service.ai.AiProviderConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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
@RequestMapping(ApiConstant.ADMIN_AI_PROVIDER_CONFIGS)
@PreAuthorize("hasRole('ADMIN')")
@Tag(
        name = "AI Provider Administration",
            description = "ADMIN-only purpose and model configuration management")
public class AiProviderConfigController {

    private final AiProviderConfigService service;

    public AiProviderConfigController(AiProviderConfigService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Create an AI provider configuration",
            description = "Binds a registered provider and model to one AI purpose. Credentials are managed separately.")
    public ApiResponse<AiProviderConfigResponse> create(
            @Valid @RequestBody CreateAiProviderConfigRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.create(actorId(jwt), actorEmail(jwt), request));
    }

    @GetMapping
    @Operation(summary = "List active AI provider configurations")
    public ApiResponse<List<AiProviderConfigResponse>> list() {
        return ApiResponse.of(service.list());
    }

    @GetMapping(ApiConstant.AI_PROVIDER_CONFIG_BY_ID)
    @Operation(summary = "Get one active AI provider configuration")
    public ApiResponse<AiProviderConfigResponse> get(@PathVariable UUID configId) {
        return ApiResponse.of(service.get(configId));
    }

    @PutMapping(ApiConstant.AI_PROVIDER_CONFIG_BY_ID)
    @Operation(summary = "Update an AI provider configuration")
    public ApiResponse<AiProviderConfigResponse> update(
            @PathVariable UUID configId,
            @Valid @RequestBody UpdateAiProviderConfigRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(
                service.update(actorId(jwt), actorEmail(jwt), configId, request));
    }

    @PostMapping(ApiConstant.AI_PROVIDER_CONFIG_ENABLE)
    @Operation(summary = "Enable a non-default provider configuration")
    public ApiResponse<AiProviderConfigResponse> enable(
            @PathVariable UUID configId,
            @Valid @RequestBody AiProviderActionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.enable(
                actorId(jwt), actorEmail(jwt), configId, request.version()));
    }

    @PostMapping(ApiConstant.AI_PROVIDER_CONFIG_DISABLE)
    @Operation(
            summary = "Disable a provider configuration",
            description = "The current default cannot be disabled until another configuration becomes default.")
    public ApiResponse<AiProviderConfigResponse> disable(
            @PathVariable UUID configId,
            @Valid @RequestBody AiProviderActionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.disable(
                actorId(jwt), actorEmail(jwt), configId, request.version()));
    }

    @PostMapping(ApiConstant.AI_PROVIDER_CONFIG_DEFAULT)
    @Operation(
            summary = "Select the default configuration for its purpose",
            description = "Selection is explicit. The system does not automatically fail over to another provider.")
    public ApiResponse<AiProviderConfigResponse> makeDefault(
            @PathVariable UUID configId,
            @Valid @RequestBody AiProviderActionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.makeDefault(
                actorId(jwt), actorEmail(jwt), configId, request.version()));
    }

    @PostMapping(ApiConstant.AI_PROVIDER_TEST_CONNECTION)
    @Operation(
            summary = "Test the selected provider and model",
            description = "Sends one minimal non-thinking request with no retry. Provider response content is discarded.",
            responses = {
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
                        responseCode = "200",
                        description = "Sanitized connection result, including failure results"),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
                        responseCode = "403",
                        description = "Only ADMIN may test provider connections",
                        content = @Content(schema = @Schema(implementation = ApiError.class)))
            })
    public ApiResponse<AiProviderConnectionTestResponse> testConnection(
            @PathVariable UUID configId, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(
                service.testConnection(actorId(jwt), actorEmail(jwt), configId));
    }

    @DeleteMapping(ApiConstant.AI_PROVIDER_CONFIG_BY_ID)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Archive a provider configuration",
            description = "Configurations are never hard-deleted. A current default must be replaced first.")
    public void archive(
            @PathVariable UUID configId,
            @RequestParam @Min(0) long version,
            @AuthenticationPrincipal Jwt jwt) {
        service.archive(actorId(jwt), actorEmail(jwt), configId, version);
    }

    private UUID actorId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private String actorEmail(Jwt jwt) {
        return jwt.getClaimAsString("email");
    }
}
