package com.codegym.aiplanning.service.report;

import com.codegym.aiplanning.controller.report.dto.DashboardReportResponse;
import java.util.UUID;

public interface DashboardReportService {

    DashboardReportResponse getDashboardReport(UUID userId);
}
