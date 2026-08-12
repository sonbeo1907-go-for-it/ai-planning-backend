package com.codegym.aiplanning.repository.daily;

import com.codegym.aiplanning.entity.daily.DailyPlanItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyPlanItemRepository extends JpaRepository<DailyPlanItem, UUID> {

    List<DailyPlanItem> findByDailyPlanVersionIdOrderByOrderIndexAsc(UUID dailyPlanVersionId);
}
