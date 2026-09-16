package com.codegym.aiplanning.controller.report.dto;

import java.time.LocalDate;

public record DailyStudyTimePointDto(
        LocalDate date,
        String dayOfWeek,
        int studyMinutes,
        int completedTasks,
        int targetMinutes) {}
