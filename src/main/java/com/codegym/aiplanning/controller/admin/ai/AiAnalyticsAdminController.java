package com.codegym.aiplanning.controller.admin.ai;

import com.codegym.aiplanning.common.api.ApiResponse;
import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.controller.admin.ai.dto.AiExecutionAnalyticsResponse;
import com.codegym.aiplanning.service.ai.AiAnalyticsAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstant.ADMIN_AI_ANALYTICS)
@PreAuthorize("hasRole('ADMIN')")
@Tag(
        name = "AI Analytics",
        description = "ADMIN-only execution metrics, token consumption, and performance analytics")
public class AiAnalyticsAdminController {

    private final AiAnalyticsAdminService analyticsService;

    public AiAnalyticsAdminController(AiAnalyticsAdminService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping
    @Operation(summary = "Get aggregated AI execution metrics within a time range")
    public ApiResponse<List<AiExecutionAnalyticsResponse>> getAnalytics(
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ApiResponse.of(analyticsService.getAnalytics(from, to));
    }
}
