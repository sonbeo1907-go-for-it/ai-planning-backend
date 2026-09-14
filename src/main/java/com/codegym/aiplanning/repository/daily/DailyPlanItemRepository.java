package com.codegym.aiplanning.repository.daily;

import com.codegym.aiplanning.entity.daily.DailyPlanItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailyPlanItemRepository extends JpaRepository<DailyPlanItem, UUID> {

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
