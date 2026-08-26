package com.codegym.aiplanning.repository.daily;

import com.codegym.aiplanning.entity.daily.DailyPlanItemAiSuggestionReference;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailyPlanItemAiSuggestionReferenceRepository
        extends JpaRepository<DailyPlanItemAiSuggestionReference, UUID> {

    List<DailyPlanItemAiSuggestionReference> findBySuggestionId(UUID suggestionId);

    @Modifying
    @Query("delete from DailyPlanItemAiSuggestionReference reference "
            + "where reference.suggestionId = :suggestionId")
    void deleteBySuggestionId(@Param("suggestionId") UUID suggestionId);
}
