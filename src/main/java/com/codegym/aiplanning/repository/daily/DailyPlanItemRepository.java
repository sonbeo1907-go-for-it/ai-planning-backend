package com.codegym.aiplanning.repository.daily;

import com.codegym.aiplanning.entity.daily.DailyPlanItem;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailyPlanItemRepository extends JpaRepository<DailyPlanItem, UUID> {

    @Query("select item from DailyPlanItem item, "
            + "DailyPlanVersion version, DailyPlan plan "
            + "where item.id = :itemId "
            + "and item.removedAt is null "
            + "and item.dailyPlanVersionId = version.id "
            + "and version.id = :versionId "
            + "and version.dailyPlanId = plan.id "
            + "and plan.id = :planId "
            + "and plan.userId = :ownerId")
    Optional<DailyPlanItem> findOwnedByPath(
            @Param("ownerId") UUID ownerId,
            @Param("planId") UUID planId,
            @Param("versionId") UUID versionId,
            @Param("itemId") UUID itemId);

    @Query("select item from DailyPlanItem item, "
            + "DailyPlanVersion version, DailyPlan plan "
            + "where item.id = :itemId "
            + "and item.removedAt is null "
            + "and item.dailyPlanVersionId = version.id "
            + "and version.dailyPlanId = plan.id "
            + "and plan.userId = :ownerId")
    Optional<DailyPlanItem> findOwnedById(
            @Param("ownerId") UUID ownerId,
            @Param("itemId") UUID itemId);

    @Query("select item from DailyPlanItem item "
            + "where item.dailyPlanVersionId = :versionId "
            + "and item.removedAt is null "
            + "order by item.orderIndex")
    List<DailyPlanItem> findByDailyPlanVersionIdOrderByOrderIndexAsc(
            @Param("versionId") UUID dailyPlanVersionId);

    @Query("select item from DailyPlanItem item "
            + "where item.dailyPlanVersionId in :versionIds "
            + "and item.removedAt is null "
            + "order by item.dailyPlanVersionId, item.orderIndex")
    List<DailyPlanItem> findByDailyPlanVersionIds(
            @Param("versionIds") List<UUID> versionIds);

    @Query("select item from DailyPlanItem item "
            + "where item.dailyPlanVersionId in :versionIds "
            + "order by item.createdAt desc")
    List<DailyPlanItem> findIncludingRemovedByDailyPlanVersionIds(
            @Param("versionIds") List<UUID> versionIds);

}
