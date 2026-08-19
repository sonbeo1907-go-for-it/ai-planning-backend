package com.codegym.aiplanning.service.daily.ai;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DailyPlanningContext(
        LocalDate targetDate,
        int availableMinutes,
        Map<String, Object> roadmapVersion, // Representing Roadmap info dynamically for now
        List<Map<String, Object>> progress,
        List<Map<String, Object>> unfinishedTasks,
        List<Map<String, Object>> weakTopics,
        Map<String, Object> previousPlan
) {}
