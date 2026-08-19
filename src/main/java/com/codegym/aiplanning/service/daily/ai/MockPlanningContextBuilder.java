package com.codegym.aiplanning.service.daily.ai;

import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MockPlanningContextBuilder implements PlanningContextBuilder {
    @Override
    public DailyPlanningContext buildContext(UUID dailyPlanId) {
        return new DailyPlanningContext(
                LocalDate.now(),
                120,
                Map.of("id", UUID.randomUUID().toString(), "milestones", List.of()),
                List.of(),
                List.of(),
                List.of(),
                Map.of("items", List.of(
                        Map.of("id", UUID.randomUUID().toString(), "roadmapItemId", "00000000-0000-0000-0000-000000000001", "title", "Study Java Basics", "plannedMinutes", 60, "status", "NOT_STARTED")
                ))
        );
    }
}
