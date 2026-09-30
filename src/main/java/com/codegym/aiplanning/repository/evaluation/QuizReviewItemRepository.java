package com.codegym.aiplanning.repository.evaluation;

import com.codegym.aiplanning.entity.evaluation.QuizReviewItem;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizReviewItemRepository extends JpaRepository<QuizReviewItem, UUID> {

    List<QuizReviewItem> findByQuizId(UUID quizId);

    @Query("""
        SELECT qri.dailyPlanItem.id FROM QuizReviewItem qri
        WHERE qri.quiz.id = :quizId
    """)
    List<UUID> findCoveredItemIdsByQuizId(@Param("quizId") UUID quizId);

    @Query("""
        SELECT DISTINCT qri.dailyPlanItem.id FROM QuizReviewItem qri
        JOIN QuizAttempt qa ON qa.quiz.id = qri.quiz.id
        WHERE qri.quiz.dailyPlanVersion.id = :versionId
          AND qa.passed = true
    """)
    Set<UUID> findPassedDailyPlanItemIdsByVersionId(@Param("versionId") UUID versionId);

    @Query("""
        SELECT COUNT(qri) > 0 FROM QuizReviewItem qri
        JOIN QuizAttempt qa ON qa.quiz.id = qri.quiz.id
        WHERE qri.dailyPlanItem.id = :itemId
          AND qa.passed = true
    """)
    boolean isItemPassedByQuiz(@Param("itemId") UUID itemId);
}
