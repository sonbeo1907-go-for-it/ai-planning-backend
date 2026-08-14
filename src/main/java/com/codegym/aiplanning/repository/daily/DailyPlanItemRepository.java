package com.codegym.aiplanning.repository.daily;

import com.codegym.aiplanning.entity.daily.DailyPlanItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailyPlanItemRepository extends JpaRepository<DailyPlanItem, UUID> {

    List<DailyPlanItem> findByDailyPlanVersionIdOrderByOrderIndexAsc(UUID dailyPlanVersionId);

    @Query("select item from DailyPlanItem item "
            + "where item.dailyPlanVersionId in :versionIds "
            + "order by item.dailyPlanVersionId, item.orderIndex")
    List<DailyPlanItem> findByDailyPlanVersionIds(
            @Param("versionIds") List<UUID> versionIds);

}
