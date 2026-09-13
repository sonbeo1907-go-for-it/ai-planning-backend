package com.codegym.aiplanning.repository.daily;

import com.codegym.aiplanning.entity.daily.ProgressEntry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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

    Optional<ProgressEntry> findByIdAndUserId(UUID id, UUID userId);

    Optional<ProgressEntry> findByUserIdAndIdempotencyKey(
            UUID userId, String idempotencyKey);

    boolean existsBySupersedesEntryId(UUID supersedesEntryId);
}
