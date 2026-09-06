package com.codegym.aiplanning.repository.evaluation;

import com.codegym.aiplanning.entity.evaluation.DailyEvaluation;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DailyEvaluationRepository extends JpaRepository<DailyEvaluation, UUID> {

    Optional<DailyEvaluation> findByDailyPlanId(UUID dailyPlanId);

    Optional<DailyEvaluation> findByUserIdAndDailyPlanVersionId(
            UUID userId,
            UUID dailyPlanVersionId);

    Optional<DailyEvaluation> findByUserIdAndEvaluationDate(UUID userId, LocalDate evaluationDate);
}
