package com.codegym.aiplanning.service.daily.ai;

import java.util.UUID;

public interface PlanningContextBuilder {
    DailyPlanningContext buildContext(UUID dailyPlanId, UUID userId);
}
