package com.codegym.aiplanning.service.report;

import com.codegym.aiplanning.controller.report.dto.DashboardReportResponse;
import com.codegym.aiplanning.controller.report.dto.KnowledgeMapResponse;
import com.codegym.aiplanning.controller.report.dto.WeakTopicTimelineItemDto;
import java.util.List;
import java.util.UUID;

public interface DashboardReportService {

    DashboardReportResponse getDashboardReport(UUID userId, UUID roadmapId);

    KnowledgeMapResponse getKnowledgeMap(UUID userId, UUID roadmapId);

    List<WeakTopicTimelineItemDto> getWeakTopicsTimeline(UUID userId, UUID roadmapId);
}
