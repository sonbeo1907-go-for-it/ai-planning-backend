package com.codegym.aiplanning.repository.daily;

import com.codegym.aiplanning.entity.daily.ProgressEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgressEntryRepository extends JpaRepository<ProgressEntry, UUID> {

    List<ProgressEntry> findByUserIdOrderByRecordedAtDesc(UUID userId);

    List<ProgressEntry> findByDailyPlanItemIdOrderByRecordedAtDesc(UUID dailyPlanItemId);
}
