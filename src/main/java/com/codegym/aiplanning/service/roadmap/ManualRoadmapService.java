package com.codegym.aiplanning.service.roadmap;

import com.codegym.aiplanning.controller.roadmap.dto.CreateMilestoneRequest;
import com.codegym.aiplanning.controller.roadmap.dto.CreateRoadmapRequest;
import com.codegym.aiplanning.controller.roadmap.dto.CreateTopicRequest;
import com.codegym.aiplanning.controller.roadmap.dto.RoadmapItemResponse;
import com.codegym.aiplanning.controller.roadmap.dto.RoadmapResponse;
import com.codegym.aiplanning.controller.roadmap.dto.RoadmapVersionResponse;
import com.codegym.aiplanning.controller.roadmap.dto.UpdateRoadmapItemRequest;
import java.util.List;
import java.util.UUID;

public interface ManualRoadmapService {

    RoadmapResponse create(UUID userId, CreateRoadmapRequest request);

    List<RoadmapResponse> list(UUID userId);

    RoadmapResponse get(UUID userId, UUID roadmapId);

    RoadmapVersionResponse createDraftVersion(UUID userId, UUID roadmapId);

    RoadmapVersionResponse getVersion(UUID userId, UUID roadmapId, UUID versionId);

    RoadmapItemResponse addMilestone(
            UUID userId, UUID roadmapId, UUID versionId, CreateMilestoneRequest request);

    RoadmapItemResponse addTopic(
            UUID userId,
            UUID roadmapId,
            UUID versionId,
            UUID milestoneId,
            CreateTopicRequest request);

    RoadmapItemResponse updateItem(
            UUID userId,
            UUID roadmapId,
            UUID versionId,
            UUID itemId,
            UpdateRoadmapItemRequest request);

    void deleteItem(UUID userId, UUID roadmapId, UUID versionId, UUID itemId);

    RoadmapVersionResponse activate(UUID userId, UUID roadmapId, UUID versionId);
}
