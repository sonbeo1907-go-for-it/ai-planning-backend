package com.codegym.aiplanning.repository.daily;

import com.codegym.aiplanning.entity.daily.DailyPlanVersion;
import com.codegym.aiplanning.entity.daily.DailyPlanVersionStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailyPlanVersionRepository extends JpaRepository<DailyPlanVersion, UUID> {

    Optional<DailyPlanVersion> findByDailyPlanIdAndVersionNumber(UUID dailyPlanId, Integer versionNumber);

    List<DailyPlanVersion> findByDailyPlanIdOrderByVersionNumberDesc(UUID dailyPlanId);

    List<DailyPlanVersion> findByDailyPlanIdIn(List<UUID> dailyPlanIds);

    Optional<DailyPlanVersion> findTopByDailyPlanIdOrderByVersionNumberDesc(UUID dailyPlanId);

    Optional<DailyPlanVersion> findByIdAndDailyPlanId(UUID versionId, UUID dailyPlanId);

    Optional<DailyPlanVersion> findByDailyPlanIdAndStatus(
            UUID dailyPlanId, DailyPlanVersionStatus status);

    Optional<DailyPlanVersion> findByDailyPlanIdAndGenerationRequestKey(
            UUID dailyPlanId, String generationRequestKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select version from DailyPlanVersion version "
            + "where version.id = :versionId and version.dailyPlanId = :dailyPlanId")
    Optional<DailyPlanVersion> findByIdAndDailyPlanIdForUpdate(
            @Param("versionId") UUID versionId,
            @Param("dailyPlanId") UUID dailyPlanId);

    @Query("select version from DailyPlanVersion version, DailyPlan plan "
            + "where version.dailyPlanId = plan.id "
            + "and plan.id in :planIds "
            + "and ((plan.activeVersionId is not null and version.id = plan.activeVersionId) "
            + "or (plan.activeVersionId is null and version.versionNumber = "
            + "(select max(candidate.versionNumber) from DailyPlanVersion candidate "
            + "where candidate.dailyPlanId = plan.id)))")
    List<DailyPlanVersion> findCurrentVersionsByDailyPlanIds(
            @Param("planIds") List<UUID> planIds);
}
