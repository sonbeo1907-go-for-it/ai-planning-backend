package com.codegym.aiplanning.controller.evaluation;

import com.codegym.aiplanning.common.api.ApiResponse;
import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.controller.ai.dto.AiExecutionResponse;
import com.codegym.aiplanning.controller.evaluation.dto.MasteryCheckResultResponse;
import com.codegym.aiplanning.controller.evaluation.dto.SubmitQuizRequest;
import com.codegym.aiplanning.service.ai.execution.AiExecutionService;
import com.codegym.aiplanning.service.evaluation.WeakTopicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstant.WEAK_TOPICS)
@PreAuthorize("hasRole('USER')")
@Tag(name = "Weak Topic Mastery", description = "Owner-scoped mastery checks")
public class WeakTopicController {

    private final AiExecutionService aiExecutionService;
    private final WeakTopicService weakTopicService;

    public WeakTopicController(
            AiExecutionService aiExecutionService,
            WeakTopicService weakTopicService) {
        this.aiExecutionService = aiExecutionService;
        this.weakTopicService = weakTopicService;
    }

    @PostMapping(ApiConstant.WEAK_TOPIC_MASTERY_CHECK_GENERATE)
    @Operation(summary = "Queue an AI mastery check")
    public ResponseEntity<ApiResponse<AiExecutionResponse>> generate(
            @PathVariable UUID weakTopicId,
            @RequestHeader(value = "Idempotency-Key", required = false)
                    String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt) {
        AiExecutionResponse execution = aiExecutionService
                .submitMasteryCheckGeneration(
                        UUID.fromString(jwt.getSubject()),
                        weakTopicId,
                        idempotencyKey);
        return ResponseEntity.accepted()
                .location(URI.create(ApiConstant.AI_EXECUTIONS + "/" + execution.id()))
                .body(ApiResponse.of(execution));
    }

    @PostMapping(ApiConstant.WEAK_TOPIC_MASTERY_CHECK_SUBMIT)
    @Operation(summary = "Submit an owned mastery check")
    public ApiResponse<MasteryCheckResultResponse> submit(
            @PathVariable UUID weakTopicId,
            @PathVariable UUID quizId,
            @Valid @RequestBody SubmitQuizRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(weakTopicService.submitMasteryCheck(
                UUID.fromString(jwt.getSubject()),
                weakTopicId,
                quizId,
                request));
    }
}
