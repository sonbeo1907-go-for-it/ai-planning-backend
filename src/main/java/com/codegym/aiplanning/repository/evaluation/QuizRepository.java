package com.codegym.aiplanning.repository.evaluation;

import com.codegym.aiplanning.entity.evaluation.Quiz;
import com.codegym.aiplanning.entity.evaluation.QuizType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, UUID> {

    Optional<Quiz> findByIdAndUserId(UUID id, UUID userId);

    @Query("""
            SELECT q FROM Quiz q
            LEFT JOIN FETCH q.questions qn
            WHERE q.user.id = :userId
              AND q.targetWeakTopic.id = :weakTopicId
              AND q.quizType = :quizType
            ORDER BY q.createdAt DESC
            """)
    List<Quiz> findWithQuestionsByUserIdAndTargetWeakTopicIdAndQuizType(
            @Param("userId") UUID userId,
            @Param("weakTopicId") UUID weakTopicId,
            @Param("quizType") QuizType quizType);

    Optional<Quiz> findByUserIdAndDailyPlanIdAndQuizType(
            UUID userId, UUID dailyPlanId, QuizType quizType);

    Optional<Quiz> findFirstByUserIdAndDailyPlanIdAndQuizTypeOrderByCreatedAtDesc(
            UUID userId, UUID dailyPlanId, QuizType quizType);

    List<Quiz> findByUserIdAndDailyPlanIdOrderByCreatedAtDesc(UUID userId, UUID dailyPlanId);

    @Query("""
            SELECT q FROM Quiz q
            LEFT JOIN FETCH q.questions qn
            WHERE q.id = :id AND q.user.id = :userId
            """)
    Optional<Quiz> findWithQuestionsByIdAndUserId(
            @Param("id") UUID id, @Param("userId") UUID userId);

    @Query("""
            SELECT q FROM Quiz q
            LEFT JOIN FETCH q.questions qn
            WHERE q.user.id = :userId AND q.dailyPlan.id = :dailyPlanId AND q.quizType = :quizType
            ORDER BY q.createdAt DESC
            """)
    List<Quiz> findWithQuestionsByUserIdAndDailyPlanIdAndQuizType(
            @Param("userId") UUID userId,
            @Param("dailyPlanId") UUID dailyPlanId,
            @Param("quizType") QuizType quizType);
}
