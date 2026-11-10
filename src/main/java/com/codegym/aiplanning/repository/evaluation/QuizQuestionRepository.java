package com.codegym.aiplanning.repository.evaluation;

import com.codegym.aiplanning.entity.evaluation.QuizQuestion;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, UUID> {

    List<QuizQuestion> findByQuizIdOrderByOrderIndexAsc(UUID quizId);
}
