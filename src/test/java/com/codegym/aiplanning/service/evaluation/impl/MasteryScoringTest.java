package com.codegym.aiplanning.service.evaluation.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.evaluation.Quiz;
import com.codegym.aiplanning.entity.evaluation.QuizAttempt;
import com.codegym.aiplanning.entity.evaluation.QuizQuestion;
import com.codegym.aiplanning.entity.evaluation.WeakTopic;
import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersion;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MasteryScoringTest {

    private final DailyEvaluationPersistenceService service =
            new DailyEvaluationPersistenceService(
                    null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null);

    @Test
    void fourOfFivePassesMasteryAndThreeOfFiveDoesNot() {
        assertScore(4, new BigDecimal("80.00"), true);
        assertScore(3, new BigDecimal("60.00"), false);
    }

    private void assertScore(int correctAnswers, BigDecimal expectedScore, boolean expectedPass) {
        Quiz quiz = Quiz.createMasteryCheck(
                mock(UserAccount.class),
                mock(Roadmap.class),
                mock(RoadmapVersion.class),
                mock(WeakTopic.class));
        Map<UUID, String> answers = new HashMap<>();
        for (int index = 0; index < 5; index++) {
            QuizQuestion question = mock(QuizQuestion.class);
            UUID id = UUID.randomUUID();
            when(question.getId()).thenReturn(id);
            when(question.getCorrectOption()).thenReturn("A");
            quiz.addQuestion(question);
            answers.put(id, index < correctAnswers ? "A" : "B");
        }

        QuizAttempt attempt = service.gradeAttempt(quiz, answers);

        assertEquals(expectedScore, attempt.getScore());
        if (expectedPass) {
            assertTrue(attempt.isPassed());
        } else {
            assertFalse(attempt.isPassed());
        }
    }
}
