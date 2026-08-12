package com.codegym.aiplanning.controller.roadmap.dto;

import com.codegym.aiplanning.entity.roadmap.RoadmapItem;
import com.codegym.aiplanning.entity.roadmap.RoadmapItemType;
import java.util.List;
import java.util.UUID;

public record RoadmapItemResponse(
        UUID id,
        long version,
        RoadmapItemType itemType,
        UUID parentItemId,
        String title,
        String description,
        int orderIndex,
        Integer estimatedMinutes,
        List<RoadmapItemResponse> topics) {

    public static RoadmapItemResponse from(
            RoadmapItem item, List<RoadmapItemResponse> topics) {
        return new RoadmapItemResponse(
                item.getId(),
                item.getVersion(),
                item.getItemType(),
                item.getParent() == null ? null : item.getParent().getId(),
                item.getTitle(),
                item.getDescription(),
                item.getOrderIndex(),
                item.getEstimatedMinutes(),
                topics == null ? List.of() : topics);
    }

    @Override
    public String toString() {
        return "RoadmapItemResponse[id=" + id
                + ", itemType=" + itemType
                + ", personalLearningData=<redacted>]";
    }
}
