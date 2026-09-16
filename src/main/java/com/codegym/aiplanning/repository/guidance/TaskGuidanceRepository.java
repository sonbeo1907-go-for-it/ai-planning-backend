package com.codegym.aiplanning.repository.guidance;

import com.codegym.aiplanning.entity.guidance.TaskGuidance;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskGuidanceRepository extends JpaRepository<TaskGuidance, UUID> {

    Optional<TaskGuidance> findByDailyPlanItemIdAndOwnerId(
            UUID dailyPlanItemId,
            UUID ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select guidance from TaskGuidance guidance "
            + "where guidance.dailyPlanItemId = :itemId "
            + "and guidance.owner.id = :ownerId")
    Optional<TaskGuidance> findOwnedByItemIdForUpdate(
            @Param("itemId") UUID dailyPlanItemId,
            @Param("ownerId") UUID ownerId);
}
