package com.codegym.aiplanning.service.evaluation.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.entity.ai.AiProviderConfig;
import com.codegym.aiplanning.entity.roadmap.RoadmapItem;
import com.codegym.aiplanning.entity.roadmap.RoadmapItemType;
import com.codegym.aiplanning.repository.roadmap.RoadmapItemRepository;
import com.codegym.aiplanning.service.evaluation.QuizGeneratorService;
import com.codegym.aiplanning.service.evaluation.impl.QuizAiGenerator.CompletedLearningUnitInfo;
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
            List<UUID> completedLearningUnitIds) {
        return generateDailyQuizQuestions(
                userId,
                dailyPlanId,
                completedLearningUnitIds,
                null);
    }

    @Override
    public GeneratedQuizPlan generateDailyQuizQuestions(
            UUID userId,
            UUID dailyPlanId,
            List<UUID> completedLearningUnitIds,
            AiProviderConfig providerConfig) {
        List<RoadmapItem> items = roadmapItemRepository.findAllOwnedByIds(
                completedLearningUnitIds,
                userId);
        long requestedLearningUnitCount = completedLearningUnitIds.stream()
                .distinct()
                .count();
        if (items.size() != requestedLearningUnitCount) {
            throw new BusinessException(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    "One or more completed Roadmap Learning Units were not found.");
        }

        List<CompletedLearningUnitInfo> learningUnitInfos = items.stream()
                .map(this::toLearningUnitInfo)
                .toList();
        return quizAiGenerator.generateDailyQuiz(learningUnitInfos, providerConfig);
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
                        "Roadmap Learning Unit was not found."));
        CompletedLearningUnitInfo learningUnitInfo = toLearningUnitInfo(item);
        return quizAiGenerator.generateMasteryCheck(learningUnitInfo, providerConfig);
    }

    private CompletedLearningUnitInfo toLearningUnitInfo(RoadmapItem learningUnit) {
        if (learningUnit.getItemType() != RoadmapItemType.LEARNING_UNIT
                || learningUnit.getRoadmapVersion() == null) {
            throw invalidLearningUnitHierarchy();
        }
        RoadmapItem topic = learningUnit.getParent();
        RoadmapItem milestone = topic == null ? null : topic.getParent();
        UUID versionId = learningUnit.getRoadmapVersion().getId();
        boolean validHierarchy = topic != null
                && topic.getItemType() == RoadmapItemType.TOPIC
                && topic.getRoadmapVersion() != null
                && versionId.equals(topic.getRoadmapVersion().getId())
                && milestone != null
                && milestone.getItemType() == RoadmapItemType.MILESTONE
                && milestone.getRoadmapVersion() != null
                && versionId.equals(milestone.getRoadmapVersion().getId());
        if (!validHierarchy) {
            throw invalidLearningUnitHierarchy();
        }
        return new CompletedLearningUnitInfo(
                learningUnit.getId(),
                learningUnit.getTitle(),
                learningUnit.getDescription() != null ? learningUnit.getDescription() : "",
                topic.getId(),
                topic.getTitle(),
                milestone.getId(),
                milestone.getTitle());
    }

    private BusinessException invalidLearningUnitHierarchy() {
        return new BusinessException(
                ErrorCode.ROADMAP_STRUCTURE_INCOMPLETE,
                "Quiz generation requires a Learning Unit inside a Topic and Milestone.");
    }
}
