package com.codegym.aiplanning.repository.daily;

import com.codegym.aiplanning.entity.daily.DailyPlanTaskStepState;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyPlanTaskStepStateRepository
        extends JpaRepository<DailyPlanTaskStepState, UUID> {

    Optional<DailyPlanTaskStepState> findByTaskStepId(UUID taskStepId);

    List<DailyPlanTaskStepState> findByTaskStepIdIn(List<UUID> taskStepIds);
}

