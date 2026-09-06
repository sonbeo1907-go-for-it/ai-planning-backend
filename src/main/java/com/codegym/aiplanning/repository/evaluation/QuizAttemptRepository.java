package com.codegym.aiplanning.repository.evaluation;

import com.codegym.aiplanning.entity.evaluation.QuizAttempt;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, UUID> {

    @EntityGraph(attributePaths = {"answers", "answers.question"})
    Optional<QuizAttempt> findFirstByQuizIdAndUserIdOrderByAttemptNumberDesc(
            UUID quizId,
            UUID userId);

    long countByQuizIdAndUserId(UUID quizId, UUID userId);
}
