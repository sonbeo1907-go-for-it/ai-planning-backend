package com.codegym.aiplanning.controller.report.dto;

import java.util.List;

public record StudyTimeDto(
        long totalStudyMinutes,
        double totalStudyHours,
        List<DailyStudyTimePointDto> dailyPoints) {}
