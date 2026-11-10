package com.codegym.aiplanning.controller.ai;

import com.codegym.aiplanning.common.api.ApiResponse;
import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.controller.ai.dto.AiExecutionResponse;
import com.codegym.aiplanning.service.ai.execution.AiExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstant.AI_EXECUTIONS)
@PreAuthorize("hasRole('USER')")
@Tag(
        name = "AI Executions",
        description = "Owner-scoped status for asynchronous personal AI work")
public class AiExecutionController {

    private final AiExecutionService aiExecutionService;

    public AiExecutionController(AiExecutionService aiExecutionService) {
        this.aiExecutionService = aiExecutionService;
    }

    @GetMapping(ApiConstant.AI_EXECUTION_BY_ID)
    @Operation(summary = "Get an owned AI execution")
    public ApiResponse<AiExecutionResponse> getExecution(
            @PathVariable UUID executionId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ApiResponse.of(
                aiExecutionService.getOwnedExecution(userId, executionId));
    }
}
