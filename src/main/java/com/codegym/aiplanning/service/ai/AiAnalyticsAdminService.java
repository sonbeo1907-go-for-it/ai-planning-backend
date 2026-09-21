package com.codegym.aiplanning.service.ai;

import com.codegym.aiplanning.controller.admin.ai.dto.AiExecutionAnalyticsResponse;
import java.time.Instant;
import java.util.List;

public interface AiAnalyticsAdminService {

    List<AiExecutionAnalyticsResponse> getAnalytics(Instant from, Instant to);
}
