package com.codegym.aiplanning.service.evaluation;

import com.codegym.aiplanning.controller.evaluation.dto.DailyEvaluationResponse;
import com.codegym.aiplanning.controller.evaluation.dto.QuizDetailResponse;
import com.codegym.aiplanning.controller.evaluation.dto.SelfEvaluationRequest;
import com.codegym.aiplanning.controller.evaluation.dto.SubmitQuizRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.codegym.aiplanning.entity.ai.AiProviderConfig;

public interface DailyEvaluationService {

    QuizDetailResponse generateDailyQuiz(UUID userId, UUID dailyPlanId);

    QuizDetailResponse generateDailyQuiz(UUID userId, UUID dailyPlanId, boolean forceNew);

    QuizDetailResponse generateDailyQuizWithProviderConfig(
            UUID userId,
            UUID dailyPlanVersionId,
            AiProviderConfig providerConfig);

    QuizDetailResponse submitDailyQuiz(
            UUID userId, UUID dailyPlanId, UUID quizId, SubmitQuizRequest request);

    Optional<QuizDetailResponse> getLatestDailyQuiz(UUID userId, UUID dailyPlanId);

    List<QuizDetailResponse> getAllDailyQuizzes(UUID userId, UUID dailyPlanId);

    QuizDetailResponse getDailyQuizById(UUID userId, UUID dailyPlanId, UUID quizId);

    DailyEvaluationResponse recordSelfEvaluation(
            UUID userId, UUID dailyPlanId, SelfEvaluationRequest request);

    DailyEvaluationResponse getDailyEvaluation(UUID userId, UUID dailyPlanId);
}
