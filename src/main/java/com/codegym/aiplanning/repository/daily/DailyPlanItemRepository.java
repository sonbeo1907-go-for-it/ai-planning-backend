package com.codegym.aiplanning.repository.daily;

import com.codegym.aiplanning.entity.daily.DailyPlanItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailyPlanItemRepository extends JpaRepository<DailyPlanItem, UUID> {

    List<DailyPlanItem> findByDailyPlanVersionIdOrderByOrderIndexAsc(UUID dailyPlanVersionId);

    @Query("select distinct item.roadmapItemId from DailyPlanItem item "
            + "join DailyPlanVersion version on item.dailyPlanVersionId = version.id "
            + "join DailyPlan plan on version.dailyPlanId = plan.id "
            + "where plan.userId = :userId and item.status = :status and item.roadmapItemId is not null")
    List<UUID> findCompletedRoadmapItemIds(
            @Param("userId") UUID userId,
            @Param("status") com.codegym.aiplanning.entity.daily.DailyTaskStatus status);
}
