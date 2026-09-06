package com.codegym.aiplanning.service.evaluation;

import com.codegym.aiplanning.controller.evaluation.dto.MasteryCheckResultResponse;
import com.codegym.aiplanning.controller.evaluation.dto.QuizDetailResponse;
import com.codegym.aiplanning.controller.evaluation.dto.SubmitQuizRequest;
import com.codegym.aiplanning.controller.evaluation.dto.WeakTopicResponse;
import com.codegym.aiplanning.entity.evaluation.WeakTopicStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.codegym.aiplanning.entity.ai.AiProviderConfig;

public interface WeakTopicService {

    void processEvaluationResult(
            UUID userId,
            UUID roadmapId,
            UUID roadmapItemId,
            BigDecimal quizScore,
            Integer understandingRating);

    List<WeakTopicResponse> getWeakTopics(
            UUID userId, UUID roadmapId, Set<WeakTopicStatus> statuses);

    QuizDetailResponse generateMasteryCheckQuiz(UUID userId, UUID weakTopicId);

    QuizDetailResponse generateMasteryCheckQuizWithProviderConfig(
            UUID userId,
            UUID weakTopicId,
            AiProviderConfig providerConfig);

    MasteryCheckResultResponse submitMasteryCheck(
            UUID userId, UUID weakTopicId, UUID quizId, SubmitQuizRequest request);

    void markInReview(UUID userId, UUID weakTopicId);

    void markInReviewByRoadmapItem(UUID userId, UUID roadmapItemId);
}
