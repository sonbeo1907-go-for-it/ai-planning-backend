package com.codegym.aiplanning.repository.daily;

import com.codegym.aiplanning.entity.daily.DailyPlanItemAiSuggestion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyPlanItemAiSuggestionRepository
        extends JpaRepository<DailyPlanItemAiSuggestion, UUID> {

    Optional<DailyPlanItemAiSuggestion> findByDailyPlanItemId(UUID dailyPlanItemId);
}
