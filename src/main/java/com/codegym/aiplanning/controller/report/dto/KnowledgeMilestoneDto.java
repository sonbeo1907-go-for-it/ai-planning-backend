package com.codegym.aiplanning.controller.report.dto;

import java.util.List;
import java.util.UUID;

public record KnowledgeMilestoneDto(
        UUID id,
        String title,
        String description,
        int orderIndex,
        List<KnowledgeTopicDto> topics) {}
