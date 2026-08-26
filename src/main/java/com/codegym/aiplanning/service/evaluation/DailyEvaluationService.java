package com.codegym.aiplanning.service.evaluation;

import com.codegym.aiplanning.controller.evaluation.dto.DailyEvaluationResponse;
import com.codegym.aiplanning.controller.evaluation.dto.QuizDetailResponse;
import com.codegym.aiplanning.controller.evaluation.dto.SelfEvaluationRequest;
import com.codegym.aiplanning.controller.evaluation.dto.SubmitQuizRequest;
import java.util.UUID;

public interface DailyEvaluationService {

    QuizDetailResponse generateDailyQuiz(UUID userId, UUID dailyPlanId);

    QuizDetailResponse submitDailyQuiz(
            UUID userId, UUID dailyPlanId, UUID quizId, SubmitQuizRequest request);

    DailyEvaluationResponse recordSelfEvaluation(
            UUID userId, UUID dailyPlanId, SelfEvaluationRequest request);

    DailyEvaluationResponse getDailyEvaluation(UUID userId, UUID dailyPlanId);
}
