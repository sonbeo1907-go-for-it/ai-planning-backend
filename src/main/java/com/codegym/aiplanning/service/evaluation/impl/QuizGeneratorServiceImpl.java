package com.codegym.aiplanning.service.evaluation.impl;

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
            UUID userId, UUID dailyPlanId, List<UUID> completedTopicItemIds) {
        List<RoadmapItem> items = roadmapItemRepository.findAllById(completedTopicItemIds);
        List<CompletedTopicInfo> topicInfos = items.stream()
                .map(item -> new CompletedTopicInfo(
                        item.getId(),
                        item.getTitle(),
                        item.getDescription() != null ? item.getDescription() : ""))
                .toList();

        return quizAiGenerator.generateDailyQuiz(topicInfos);
    }

    @Override
    public GeneratedQuizPlan generateMasteryCheckQuestions(
            UUID userId, UUID weakTopicId, UUID roadmapItemId) {
        RoadmapItem item = roadmapItemRepository.findById(roadmapItemId)
                .orElse(null);
        CompletedTopicInfo topicInfo = new CompletedTopicInfo(
                roadmapItemId,
                item != null ? item.getTitle() : "Chủ đề ôn tập",
                item != null && item.getDescription() != null ? item.getDescription() : "");

        return quizAiGenerator.generateMasteryCheck(topicInfo);
    }
}
