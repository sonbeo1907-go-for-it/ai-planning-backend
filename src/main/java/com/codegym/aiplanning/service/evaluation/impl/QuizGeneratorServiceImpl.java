package com.codegym.aiplanning.service.evaluation.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.entity.ai.AiProviderConfig;
import com.codegym.aiplanning.entity.roadmap.RoadmapItem;
import com.codegym.aiplanning.repository.roadmap.RoadmapItemRepository;
import com.codegym.aiplanning.service.evaluation.QuizGeneratorService;
import com.codegym.aiplanning.service.evaluation.impl.QuizAiGenerator.CompletedTopicInfo;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class QuizGeneratorServiceImpl implements QuizGeneratorService {

    private final QuizAiGenerator quizAiGenerator;
    private final RoadmapItemRepository roadmapItemRepository;

    public QuizGeneratorServiceImpl(
            QuizAiGenerator quizAiGenerator,
            RoadmapItemRepository roadmapItemRepository) {
        this.quizAiGenerator = quizAiGenerator;
        this.roadmapItemRepository = roadmapItemRepository;
    }

    @Override
    public GeneratedQuizPlan generateDailyQuizQuestions(
            UUID userId,
            UUID dailyPlanId,
            List<UUID> completedTopicItemIds) {
        return generateDailyQuizQuestions(
                userId,
                dailyPlanId,
                completedTopicItemIds,
                null);
    }

    @Override
    public GeneratedQuizPlan generateDailyQuizQuestions(
            UUID userId,
            UUID dailyPlanId,
            List<UUID> completedTopicItemIds,
            AiProviderConfig providerConfig) {
        List<RoadmapItem> items = roadmapItemRepository.findAllOwnedByIds(
                completedTopicItemIds,
                userId);
        long requestedTopicCount = completedTopicItemIds.stream()
                .distinct()
                .count();
        if (items.size() != requestedTopicCount) {
            throw new BusinessException(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    "One or more completed Roadmap topics were not found.");
        }

        List<CompletedTopicInfo> topicInfos = items.stream()
                .map(item -> new CompletedTopicInfo(
                        item.getId(),
                        item.getTitle(),
                        item.getDescription() != null ? item.getDescription() : ""))
                .toList();
        return quizAiGenerator.generateDailyQuiz(topicInfos, providerConfig);
    }

    @Override
    public GeneratedQuizPlan generateMasteryCheckQuestions(
            UUID userId,
            UUID weakTopicId,
            UUID roadmapItemId) {
        return generateMasteryCheckQuestions(
                userId,
                weakTopicId,
                roadmapItemId,
                null);
    }

    @Override
    public GeneratedQuizPlan generateMasteryCheckQuestions(
            UUID userId,
            UUID weakTopicId,
            UUID roadmapItemId,
            AiProviderConfig providerConfig) {
        RoadmapItem item = roadmapItemRepository.findOwnedById(roadmapItemId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Roadmap topic was not found."));
        CompletedTopicInfo topicInfo = new CompletedTopicInfo(
                roadmapItemId,
                item.getTitle(),
                item.getDescription() != null ? item.getDescription() : "");
        return quizAiGenerator.generateMasteryCheck(topicInfo, providerConfig);
    }
}
