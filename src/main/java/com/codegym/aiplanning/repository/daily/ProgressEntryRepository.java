package com.codegym.aiplanning.repository.daily;

import com.codegym.aiplanning.entity.daily.ProgressEntry;
import com.codegym.aiplanning.entity.daily.ProgressEntryStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProgressEntryRepository extends JpaRepository<ProgressEntry, UUID> {

    List<ProgressEntry> findByUserIdOrderByRecordedAtDesc(UUID userId);

    List<ProgressEntry> findByUserIdOrderByRecordedAtDesc(UUID userId, Pageable pageable);

    List<ProgressEntry> findByUserIdAndDailyPlanItemIdOrderByRecordedAtDesc(
            UUID userId, UUID dailyPlanItemId);

    List<ProgressEntry> findByUserIdAndLearningUnitIdOrderByRecordedAtAscIdAsc(
            UUID userId, UUID learningUnitId);

    List<ProgressEntry> findByDailyPlanItemIdInOrderByRecordedAtDesc(List<UUID> dailyPlanItemIds);

    List<ProgressEntry> findByUserIdAndDailyPlanItemIdInOrderByRecordedAtDesc(
            UUID userId, List<UUID> dailyPlanItemIds);

    List<ProgressEntry> findByUserIdAndStatus(UUID userId, ProgressEntryStatus status);

    List<ProgressEntry> findByUserIdAndRecordedAtGreaterThanEqualOrderByRecordedAtAsc(
            UUID userId, Instant since);

    @Query("select coalesce(sum(e.actualMinutes), 0) from ProgressEntry e where e.userId = :userId")
    Long sumActualMinutesByUserId(@Param("userId") UUID userId);

    Optional<ProgressEntry> findByIdAndUserId(UUID id, UUID userId);

    Optional<ProgressEntry> findByUserIdAndIdempotencyKey(
            UUID userId, String idempotencyKey);

    boolean existsBySupersedesEntryId(UUID supersedesEntryId);
}
