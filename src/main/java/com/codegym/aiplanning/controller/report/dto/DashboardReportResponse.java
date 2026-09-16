package com.codegym.aiplanning.controller.report.dto;

public record DashboardReportResponse(
        MasterPlanProgressDto masterPlan,
        StreakDto streak,
        StudyTimeDto studyTime) {}
