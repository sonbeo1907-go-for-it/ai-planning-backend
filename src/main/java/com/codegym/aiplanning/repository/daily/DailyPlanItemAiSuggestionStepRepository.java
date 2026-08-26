package com.codegym.aiplanning.repository.daily;

import com.codegym.aiplanning.entity.daily.DailyPlanItemAiSuggestionStep;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailyPlanItemAiSuggestionStepRepository
        extends JpaRepository<DailyPlanItemAiSuggestionStep, UUID> {

    List<DailyPlanItemAiSuggestionStep> findBySuggestionIdOrderByOrderIndexAsc(UUID suggestionId);

    @Modifying
    @Query("delete from DailyPlanItemAiSuggestionStep step "
            + "where step.suggestionId = :suggestionId")
    void deleteBySuggestionId(@Param("suggestionId") UUID suggestionId);
}
