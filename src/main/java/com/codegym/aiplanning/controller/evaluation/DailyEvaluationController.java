package com.codegym.aiplanning.controller.evaluation;

import com.codegym.aiplanning.common.api.ApiResponse;
import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.controller.ai.dto.AiExecutionResponse;
import com.codegym.aiplanning.controller.evaluation.dto.DailyEvaluationResponse;
import com.codegym.aiplanning.controller.evaluation.dto.QuizDetailResponse;
import com.codegym.aiplanning.controller.evaluation.dto.SelfEvaluationRequest;
import com.codegym.aiplanning.controller.evaluation.dto.SubmitQuizRequest;
import com.codegym.aiplanning.service.ai.execution.AiExecutionService;
import com.codegym.aiplanning.service.evaluation.DailyEvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstant.DAILY_PLANS)
@Tag(name = "Daily Evaluations & Quiz", description = "AI Micro-Quiz and Daily Evaluation APIs (US-EVL-01, US-EVL-02)")
@PreAuthorize("hasRole('USER')")
public class DailyEvaluationController {

    private final DailyEvaluationService dailyEvaluationService;
    private final AiExecutionService aiExecutionService;

    public DailyEvaluationController(
            DailyEvaluationService dailyEvaluationService,
            AiExecutionService aiExecutionService) {
        this.dailyEvaluationService = dailyEvaluationService;
        this.aiExecutionService = aiExecutionService;
    }

    @PostMapping(ApiConstant.DAILY_PLAN_QUIZ_GENERATE)
    @Operation(
            summary = "Queue AI Micro-Quiz generation for a daily plan (US-EVL-01)",
            description = "Queues asynchronous generation from completed tasks in the exact active DailyPlanVersion.")
    public ResponseEntity<ApiResponse<AiExecutionResponse>> generateDailyQuiz(
            @PathVariable UUID planId,
            @RequestHeader(value = "Idempotency-Key", required = false)
                    String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        AiExecutionResponse execution = aiExecutionService.submitDailyQuizGeneration(
                userId,
                planId,
                idempotencyKey);
        return ResponseEntity.accepted()
                .location(URI.create(ApiConstant.AI_EXECUTIONS + "/" + execution.id()))
                .body(ApiResponse.of(execution));
    }

    @GetMapping(ApiConstant.DAILY_PLAN_QUIZ_CURRENT_EXECUTION)
    @Operation(summary = "Get the latest daily quiz AI execution")
    public ApiResponse<AiExecutionResponse> getLatestDailyQuizExecution(
            @PathVariable UUID planId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ApiResponse.of(aiExecutionService.getLatestDailyQuizExecution(
                userId,
                planId));
    }

    @PostMapping(ApiConstant.DAILY_PLAN_QUIZ_SUBMIT)
    @Operation(
            summary = "Submit AI Micro-Quiz answers (US-EVL-01)",
            description = "Grades quiz answers, calculates percentage score, checks 80% passing threshold, and reveals correct answers and explanations.")
    public ApiResponse<QuizDetailResponse> submitDailyQuiz(
            @PathVariable UUID planId,
            @PathVariable UUID quizId,
            @Valid @RequestBody SubmitQuizRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ApiResponse.of(dailyEvaluationService.submitDailyQuiz(userId, planId, quizId, request));
    }

    @GetMapping(ApiConstant.DAILY_PLAN_BY_ID + "/quiz")
    @Operation(
            summary = "Get latest daily quiz (US-EVL-01)",
            description = "Returns the latest quiz for this daily plan, with answers masked if unsubmitted.")
    public ApiResponse<QuizDetailResponse> getLatestDailyQuiz(
            @PathVariable UUID planId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ApiResponse.of(dailyEvaluationService.getLatestDailyQuiz(userId, planId).orElse(null));
    }

    @GetMapping(ApiConstant.DAILY_PLAN_BY_ID + "/quizzes")
    @Operation(
            summary = "Get all daily quizzes for daily plan (US-EVL-01)",
            description = "Returns all quiz definitions for this daily plan ordered by creation time descending.")
    public ApiResponse<List<QuizDetailResponse>> getAllDailyQuizzes(
            @PathVariable UUID planId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ApiResponse.of(dailyEvaluationService.getAllDailyQuizzes(userId, planId));
    }

    @GetMapping(ApiConstant.DAILY_PLAN_BY_ID + "/quiz/{quizId}")
    @Operation(
            summary = "Get specific daily quiz by ID (US-EVL-01)",
            description = "Returns a specific quiz attempt with answers and explanations.")
    public ApiResponse<QuizDetailResponse> getDailyQuizById(
            @PathVariable UUID planId,
            @PathVariable UUID quizId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ApiResponse.of(dailyEvaluationService.getDailyQuizById(userId, planId, quizId));
    }

    @GetMapping(ApiConstant.DAILY_PLAN_EVALUATION)
    @Operation(
            summary = "Get daily evaluation summary (US-EVL-01, US-EVL-02)",
            description = "Returns daily evaluation including quiz result, rating, and feedback notes.")
    public ApiResponse<DailyEvaluationResponse> getDailyEvaluation(
            @PathVariable UUID planId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ApiResponse.of(dailyEvaluationService.getDailyEvaluation(userId, planId));
    }

    @PostMapping(ApiConstant.DAILY_PLAN_EVALUATION)
    @Operation(
            summary = "Record self-evaluation rating and notes (US-EVL-02)",
            description = "Records 1-5 star understanding rating and optional feedback notes for the daily plan.")
    public ApiResponse<DailyEvaluationResponse> recordSelfEvaluation(
            @PathVariable UUID planId,
            @Valid @RequestBody SelfEvaluationRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ApiResponse.of(dailyEvaluationService.recordSelfEvaluation(userId, planId, request));
    }
}
