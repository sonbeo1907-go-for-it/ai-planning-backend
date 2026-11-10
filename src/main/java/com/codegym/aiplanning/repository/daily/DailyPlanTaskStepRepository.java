package com.codegym.aiplanning.repository.daily;

import com.codegym.aiplanning.entity.daily.DailyPlanTaskStep;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailyPlanTaskStepRepository
        extends JpaRepository<DailyPlanTaskStep, UUID> {

    @Query("select step from DailyPlanTaskStep step "
            + "where step.dailyPlanItemId = :itemId "
            + "order by step.orderIndex")
    List<DailyPlanTaskStep> findByDailyPlanItemIdOrderByOrderIndex(
            @Param("itemId") UUID dailyPlanItemId);

    @Query("select step from DailyPlanTaskStep step "
            + "where step.dailyPlanItemId in :itemIds "
            + "order by step.dailyPlanItemId, step.orderIndex")
    List<DailyPlanTaskStep> findByDailyPlanItemIdInOrderByItemAndOrder(
            @Param("itemIds") List<UUID> dailyPlanItemIds);

    @Query("select step from DailyPlanTaskStep step, "
            + "DailyPlanItem item, DailyPlanVersion planVersion, DailyPlan plan "
            + "where step.id = :stepId "
            + "and step.dailyPlanItemId = item.id "
            + "and item.id = :itemId "
            + "and item.removedAt is null "
            + "and item.dailyPlanVersionId = planVersion.id "
            + "and planVersion.id = :versionId "
            + "and planVersion.dailyPlanId = plan.id "
            + "and plan.id = :planId "
            + "and plan.userId = :userId")
    Optional<DailyPlanTaskStep> findOwnedByPath(
            @Param("planId") UUID planId,
            @Param("versionId") UUID versionId,
            @Param("itemId") UUID itemId,
            @Param("stepId") UUID stepId,
            @Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select step from DailyPlanTaskStep step, "
            + "DailyPlanItem item, DailyPlanVersion planVersion, DailyPlan plan "
            + "where step.id = :stepId "
            + "and step.dailyPlanItemId = item.id "
            + "and item.id = :itemId "
            + "and item.removedAt is null "
            + "and item.dailyPlanVersionId = planVersion.id "
            + "and planVersion.id = :versionId "
            + "and planVersion.dailyPlanId = plan.id "
            + "and plan.id = :planId "
            + "and plan.userId = :userId")
    Optional<DailyPlanTaskStep> findOwnedByPathForUpdate(
            @Param("planId") UUID planId,
            @Param("versionId") UUID versionId,
            @Param("itemId") UUID itemId,
            @Param("stepId") UUID stepId,
            @Param("userId") UUID userId);
}
