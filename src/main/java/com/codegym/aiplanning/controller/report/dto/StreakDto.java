package com.codegym.aiplanning.controller.report.dto;

import java.time.LocalDate;

public record StreakDto(
        int currentStreak,
        int longestStreak,
        boolean isActiveToday,
        LocalDate lastActiveDate,
        String timeZone) {}
