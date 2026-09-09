package com.codegym.aiplanning.service.roadmap;

import com.codegym.aiplanning.controller.roadmap.dto.CreateLearningUnitRequest;
import com.codegym.aiplanning.controller.roadmap.dto.CreateMilestoneRequest;
import com.codegym.aiplanning.controller.roadmap.dto.CreateRoadmapRequest;
import com.codegym.aiplanning.controller.roadmap.dto.CreateTopicRequest;
import com.codegym.aiplanning.controller.roadmap.dto.RoadmapItemResponse;
import com.codegym.aiplanning.controller.roadmap.dto.RoadmapResponse;
import com.codegym.aiplanning.controller.roadmap.dto.RoadmapSummaryResponse;
import com.codegym.aiplanning.controller.roadmap.dto.RoadmapVersionResponse;
import com.codegym.aiplanning.controller.roadmap.dto.UpdateRoadmapItemRequest;
import com.codegym.aiplanning.controller.roadmap.dto.UpdateRoadmapRequest;
import com.codegym.aiplanning.entity.roadmap.RoadmapStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ManualRoadmapService {

    RoadmapResponse create(UUID userId, CreateRoadmapRequest request);

    Page<RoadmapSummaryResponse> list(
            UUID userId, String query, RoadmapStatus status, Pageable pageable);

    RoadmapResponse get(UUID userId, UUID roadmapId);

    RoadmapResponse createEditableCopy(UUID userId, UUID roadmapId);

    RoadmapResponse update(
            UUID userId, UUID roadmapId, UpdateRoadmapRequest request);

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

    RoadmapItemResponse addLearningUnit(
            UUID userId,
            UUID roadmapId,
            UUID versionId,
            UUID topicId,
            CreateLearningUnitRequest request);

    RoadmapItemResponse updateItem(
            UUID userId,
            UUID roadmapId,
            UUID versionId,
            UUID itemId,
            UpdateRoadmapItemRequest request);

    void deleteItem(UUID userId, UUID roadmapId, UUID versionId, UUID itemId);

    RoadmapVersionResponse activate(UUID userId, UUID roadmapId, UUID versionId);
}
