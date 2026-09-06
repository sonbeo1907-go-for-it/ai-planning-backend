package com.codegym.aiplanning.service.evaluation;

import com.codegym.aiplanning.controller.evaluation.dto.QuizOptionDto;
import java.util.List;
import java.util.UUID;
import com.codegym.aiplanning.entity.ai.AiProviderConfig;

public interface QuizGeneratorService {

    record GeneratedQuestion(
            UUID roadmapItemId,
            String questionText,
            List<QuizOptionDto> options,
            String correctOption,
            String explanation,
            int orderIndex
    ) {}

    record GeneratedQuizPlan(
            List<GeneratedQuestion> questions
    ) {}

    GeneratedQuizPlan generateDailyQuizQuestions(
            UUID userId, UUID dailyPlanId, List<UUID> completedTopicItemIds);

    GeneratedQuizPlan generateDailyQuizQuestions(
            UUID userId,
            UUID dailyPlanId,
            List<UUID> completedTopicItemIds,
            AiProviderConfig providerConfig);

    GeneratedQuizPlan generateMasteryCheckQuestions(
            UUID userId, UUID weakTopicId, UUID roadmapItemId);

    GeneratedQuizPlan generateMasteryCheckQuestions(
            UUID userId,
            UUID weakTopicId,
            UUID roadmapItemId,
            AiProviderConfig providerConfig);
}
