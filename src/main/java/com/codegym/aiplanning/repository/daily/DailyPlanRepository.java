package com.codegym.aiplanning.repository.daily;

import com.codegym.aiplanning.entity.daily.DailyPlan;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyPlanRepository extends JpaRepository<DailyPlan, UUID> {

    Optional<DailyPlan> findByUserIdAndPlanDate(UUID userId, LocalDate planDate);

    Optional<DailyPlan> findByIdAndUserId(UUID id, UUID userId);

    List<DailyPlan> findByUserIdOrderByPlanDateDesc(UUID userId);
}
