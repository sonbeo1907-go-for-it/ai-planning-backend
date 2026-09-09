package com.codegym.aiplanning.controller.daily.dto;

import com.codegym.aiplanning.entity.roadmap.RoadmapItemProgressStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Available Learning Unit candidate from the active roadmap for daily plan task linkage (RMP-PROG-02)")
public record AvailableLearningUnitResponse(
        UUID id,
        String title,
        String description,
        Integer estimatedMinutes,
        Integer orderIndex,
        UUID topicId,
        String topicTitle,
        UUID milestoneId,
        String milestoneTitle,
        RoadmapItemProgressStatus progressStatus
) {}
