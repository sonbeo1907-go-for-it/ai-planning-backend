package com.codegym.aiplanning.service.daily.ai;

import com.codegym.aiplanning.entity.daily.DailyPlan;
import com.codegym.aiplanning.entity.daily.DailyPlanItem;
import com.codegym.aiplanning.entity.material.Material;
import com.codegym.aiplanning.entity.material.MaterialStatus;
import com.codegym.aiplanning.entity.roadmap.RoadmapItem;
import com.codegym.aiplanning.entity.roadmap.RoadmapSource;
import com.codegym.aiplanning.repository.daily.DailyPlanItemRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapItemRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapSourceRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Builds the AI context for one DailyPlanItem suggestion (US-TSK-AI).
 * Includes the task itself, its Roadmap topic, the user's goal, and a bounded
 * preview of the original documents (materials) linked to the plan's Roadmap.
 */
@Component
public class TaskSuggestionContextBuilder {

    private static final int MAX_SOURCE_COUNT = 10;
    private static final int MAX_SOURCE_CHARACTERS = 6000;
    private static final int MAX_TOTAL_SOURCE_CHARACTERS = 24000;

    private final DailyPlanItemRepository dailyPlanItemRepository;
    private final RoadmapItemRepository roadmapItemRepository;
    private final RoadmapSourceRepository roadmapSourceRepository;

    public TaskSuggestionContextBuilder(
            DailyPlanItemRepository dailyPlanItemRepository,
            RoadmapItemRepository roadmapItemRepository,
            RoadmapSourceRepository roadmapSourceRepository) {
        this.dailyPlanItemRepository = dailyPlanItemRepository;
        this.roadmapItemRepository = roadmapItemRepository;
        this.roadmapSourceRepository = roadmapSourceRepository;
    }

    public TaskSuggestionContext build(DailyPlan plan, DailyPlanItem item) {
        return new TaskSuggestionContext(
                item.getId(),
                item.getTitle(),
                item.getDescription(),
                item.getCategory(),
                item.getPlannedMinutes(),
                roadmapTopic(item),
                goal(plan),
                sources(plan));
    }

    private TaskSuggestionContext.RoadmapTopicContext roadmapTopic(DailyPlanItem item) {
        if (item.getRoadmapItemId() == null) {
            return null;
        }
        return roadmapItemRepository.findById(item.getRoadmapItemId())
                .map(topic -> new TaskSuggestionContext.RoadmapTopicContext(
                        topic.getId(),
                        topic.getParent() == null ? null : topic.getParent().getTitle(),
                        topic.getTitle(),
                        topic.getDescription()))
                .orElse(null);
    }

    private String goal(DailyPlan plan) {
        if (plan.getRoadmapId() == null) {
            return null;
        }
        return roadmapSourceRepository.findGoalSourceByRoadmapId(plan.getRoadmapId())
                .map(link -> link.getLearningSource() == null
                        ? null
                        : link.getLearningSource().getContentText())
                .orElse(null);
    }

    private List<TaskSuggestionContext.SourceDocument> sources(DailyPlan plan) {
        if (plan.getRoadmapId() == null) {
            return List.of();
        }
        List<TaskSuggestionContext.SourceDocument> documents = new ArrayList<>();
        int remainingCharacters = MAX_TOTAL_SOURCE_CHARACTERS;
        for (RoadmapSource link : roadmapSourceRepository.findByRoadmapId(plan.getRoadmapId())) {
            Material material = link.getMaterial();
            if (material == null
                    || material.getStatus() != MaterialStatus.READY
                    || material.isArchived()
                    || material.getContent() == null
                    || material.getContent().isBlank()) {
                continue;
            }
            String title = material.getOriginalFileName() != null
                    ? material.getOriginalFileName()
                    : material.getId().toString();
            String content = limitedContent(material.getContent(), remainingCharacters);
            if (content.isEmpty()) {
                break;
            }
            documents.add(new TaskSuggestionContext.SourceDocument(
                    material.getId(), "MATERIAL", title, content));
            remainingCharacters -= content.length();
            if (documents.size() == MAX_SOURCE_COUNT || remainingCharacters == 0) {
                break;
            }
        }
        return documents;
    }

    private String limitedContent(String content, int remainingCharacters) {
        String normalized = content.trim();
        int maximumLength = Math.min(MAX_SOURCE_CHARACTERS, remainingCharacters);
        if (normalized.length() <= maximumLength) {
            return normalized;
        }
        return normalized.substring(0, maximumLength);
    }
}
