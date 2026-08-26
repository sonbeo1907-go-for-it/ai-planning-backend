package com.codegym.aiplanning.repository.daily;

import com.codegym.aiplanning.entity.daily.DailyPlan;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailyPlanRepository extends JpaRepository<DailyPlan, UUID> {

    Optional<DailyPlan> findByUserIdAndPlanDate(UUID userId, LocalDate planDate);

    Optional<DailyPlan> findByIdAndUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from DailyPlan plan "
            + "where plan.id = :planId and plan.userId = :userId")
    Optional<DailyPlan> findByIdAndUserIdForUpdate(
            @Param("planId") UUID planId, @Param("userId") UUID userId);

    @Query("select plan from DailyPlan plan "
            + "where plan.userId = :userId "
            + "and (:status is null or plan.status = :status) "
            + "and (:roadmapId is null or plan.roadmapId = :roadmapId) "
            + "and (:fromDate is null or plan.planDate >= :fromDate) "
            + "and (:toDate is null or plan.planDate <= :toDate)")
    Page<DailyPlan> searchOwned(
            @Param("userId") UUID userId,
            @Param("status") com.codegym.aiplanning.entity.daily.DailyPlanStatus status,
            @Param("roadmapId") UUID roadmapId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable);

    Optional<DailyPlan> findFirstByUserIdAndRoadmapIdAndPlanDateBeforeOrderByPlanDateDesc(
            UUID userId, UUID roadmapId, LocalDate planDate);
}
