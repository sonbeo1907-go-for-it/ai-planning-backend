package com.codegym.aiplanning.controller.report;

import com.codegym.aiplanning.common.api.ApiResponse;
import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.controller.report.dto.DashboardReportResponse;
import com.codegym.aiplanning.controller.report.dto.KnowledgeMapResponse;
import com.codegym.aiplanning.controller.report.dto.WeakTopicTimelineItemDto;
import com.codegym.aiplanning.service.report.DashboardReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstant.REPORTS)
@PreAuthorize("hasRole('USER')")
@Tag(name = "Reports & Analytics", description = "User progress analytics, streak calculation, knowledge map, and study time reporting (US-REP-01, US-REP-02)")
public class DashboardReportController {

    private final DashboardReportService dashboardReportService;

    public DashboardReportController(DashboardReportService dashboardReportService) {
        this.dashboardReportService = dashboardReportService;
    }

    @GetMapping(ApiConstant.DASHBOARD)
    @Operation(
            summary = "Get user dashboard statistics (US-REP-01)",
            description = "Returns active Master Plan progress, consecutive learning streak accurate to user time zone, and total study time with 7-day breakdown.")
    public ApiResponse<DashboardReportResponse> getDashboardReport(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ApiResponse.of(dashboardReportService.getDashboardReport(userId));
    }

    @GetMapping(ApiConstant.KNOWLEDGE_MAP)
    @Operation(
            summary = "Get knowledge map for a roadmap (US-REP-02)",
            description = "Returns hierarchical structure of Milestones, Topics, and Learning Units with MASTERED indicators in green.")
    public ApiResponse<KnowledgeMapResponse> getKnowledgeMap(
            @RequestParam(required = false) UUID roadmapId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ApiResponse.of(dashboardReportService.getKnowledgeMap(userId, roadmapId));
    }

    @GetMapping(ApiConstant.WEAK_TOPICS_TIMELINE)
    @Operation(
            summary = "Get weak topics overcoming timeline history (US-REP-02)",
            description = "Returns historical timeline of weak topics conquered and mastered over time.")
    public ApiResponse<List<WeakTopicTimelineItemDto>> getWeakTopicsTimeline(
            @RequestParam(required = false) UUID roadmapId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ApiResponse.of(dashboardReportService.getWeakTopicsTimeline(userId, roadmapId));
    }
}
