package com.codegym.aiplanning.service.roadmap.impl;

import com.codegym.aiplanning.controller.roadmap.dto.RoadmapProgressResponse;
import com.codegym.aiplanning.entity.roadmap.RoadmapItem;
import com.codegym.aiplanning.repository.roadmap.RoadmapItemRepository;
import com.codegym.aiplanning.service.roadmap.RoadmapItemProgressService;
import com.codegym.aiplanning.service.roadmap.RoadmapItemProgressService.ItemProgressCalculationResult;
import com.codegym.aiplanning.service.roadmap.RoadmapProgressService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoadmapProgressServiceImpl implements RoadmapProgressService {

    private final RoadmapItemRepository roadmapItemRepository;
    private final RoadmapItemProgressService roadmapItemProgressService;

    public RoadmapProgressServiceImpl(
            RoadmapItemRepository roadmapItemRepository,
            RoadmapItemProgressService roadmapItemProgressService) {
        this.roadmapItemRepository = roadmapItemRepository;
        this.roadmapItemProgressService = roadmapItemProgressService;
    }

    @Override
    @Transactional(readOnly = true)
    public RoadmapProgressResponse calculateRoadmapProgress(
            UUID roadmapId, UUID activeVersionId, UUID userId) {
        if (activeVersionId == null || userId == null) {
            return RoadmapProgressResponse.empty();
        }

        List<RoadmapItem> items = roadmapItemRepository
                .findAllByRoadmapVersionIdOrderByOrderIndexAsc(activeVersionId);
        if (items.isEmpty()) {
            return RoadmapProgressResponse.empty();
        }

        int totalItemsCount = items.size();
        List<UUID> itemIds = items.stream().map(RoadmapItem::getId).toList();

        Map<UUID, ItemProgressCalculationResult> progressMap =
                roadmapItemProgressService.calculateItemsProgress(itemIds, userId);

        int completedItemsCount = 0;
        if (progressMap != null && !progressMap.isEmpty()) {
            for (UUID itemId : itemIds) {
                ItemProgressCalculationResult itemResult = progressMap.get(itemId);
                if (itemResult != null
                        && itemResult.progress() != null
                        && itemResult.progress().isCompleted()) {
                    completedItemsCount++;
                }
            }
        }

        int completionPercentage = (completedItemsCount * 100) / totalItemsCount;
        return new RoadmapProgressResponse(completionPercentage, completedItemsCount, totalItemsCount);
    }
}
