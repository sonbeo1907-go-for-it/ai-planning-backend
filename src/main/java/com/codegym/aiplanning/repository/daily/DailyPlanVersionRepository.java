package com.codegym.aiplanning.repository.daily;

import com.codegym.aiplanning.entity.daily.DailyPlanVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyPlanVersionRepository extends JpaRepository<DailyPlanVersion, UUID> {

    Optional<DailyPlanVersion> findByDailyPlanIdAndVersionNumber(UUID dailyPlanId, Integer versionNumber);

    List<DailyPlanVersion> findByDailyPlanIdOrderByVersionNumberDesc(UUID dailyPlanId);
}
