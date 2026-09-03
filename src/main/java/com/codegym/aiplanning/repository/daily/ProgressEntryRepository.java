package com.codegym.aiplanning.repository.daily;

import com.codegym.aiplanning.entity.daily.ProgressEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

public interface ProgressEntryRepository extends JpaRepository<ProgressEntry, UUID> {

    List<ProgressEntry> findByUserIdOrderByRecordedAtDesc(UUID userId);

    List<ProgressEntry> findByUserIdOrderByRecordedAtDesc(UUID userId, Pageable pageable);

    List<ProgressEntry> findByDailyPlanItemIdOrderByRecordedAtDesc(UUID dailyPlanItemId);

    List<ProgressEntry> findByUserIdAndDailyPlanItemIdInOrderByRecordedAtDesc(
            UUID userId, List<UUID> dailyPlanItemIds);

    boolean existsByDailyPlanItemId(UUID dailyPlanItemId);
}
