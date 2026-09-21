package com.codegym.aiplanning.service.evaluation.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.controller.evaluation.dto.AnswerSubmissionDto;
import com.codegym.aiplanning.controller.evaluation.dto.MasteryCheckResultResponse;
import com.codegym.aiplanning.controller.evaluation.dto.SubmitQuizRequest;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.evaluation.Quiz;
import com.codegym.aiplanning.entity.evaluation.QuizAttempt;
import com.codegym.aiplanning.entity.evaluation.QuizQuestion;
import com.codegym.aiplanning.entity.evaluation.WeakTopic;
import com.codegym.aiplanning.entity.evaluation.WeakTopicStatus;
import com.codegym.aiplanning.entity.evaluation.WeakTopicTrigger;
import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapItem;
import com.codegym.aiplanning.entity.roadmap.RoadmapItemType;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersion;
import com.codegym.aiplanning.repository.evaluation.QuizAttemptRepository;
import com.codegym.aiplanning.repository.evaluation.QuizRepository;
import com.codegym.aiplanning.repository.evaluation.WeakTopicRepository;
import com.codegym.aiplanning.repository.profile.UserProfileRepository;
import com.codegym.aiplanning.service.audit.AuditLogService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MasterySubmissionTest {

    @Mock private QuizRepository quizRepository;
    @Mock private QuizAttemptRepository quizAttemptRepository;
    @Mock private WeakTopicRepository weakTopicRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private AuditLogService auditLogService;
    @InjectMocks private DailyEvaluationPersistenceService service;

    @Test
    void fourOfFiveMastersAndReplayDoesNotCreateAnotherAttempt() {
        Fixture fixture = fixture(4);

        MasteryCheckResultResponse first = service.submitMasteryQuiz(
                fixture.userId(), fixture.weakTopicId(), fixture.quizId(), fixture.request());
        MasteryCheckResultResponse replay = service.submitMasteryQuiz(
                fixture.userId(), fixture.weakTopicId(), fixture.quizId(), fixture.request());

        assertTrue(first.isMastered());
        assertEquals(4, first.correctCount());
        assertEquals(5, first.totalCount());
        assertEquals(WeakTopicStatus.MASTERED, first.weakTopicStatus());
        assertEquals(first.attemptId(), replay.attemptId());
        verify(quizAttemptRepository, times(1)).saveAndFlush(any(QuizAttempt.class));
        assertEquals(WeakTopicStatus.MASTERED, fixture.weakTopic().getStatus());
    }

    @Test
    void threeOfFiveLeavesTheWeakTopicActive() {
        Fixture fixture = fixture(3);

        MasteryCheckResultResponse result = service.submitMasteryQuiz(
                fixture.userId(), fixture.weakTopicId(), fixture.quizId(), fixture.request());

        assertFalse(result.isMastered());
        assertEquals(3, result.correctCount());
        assertEquals(WeakTopicStatus.UNRESOLVED, result.weakTopicStatus());
        assertTrue(result.canRetry());
        assertEquals(result.quizScore(), fixture.weakTopic().getLastMasteryScore());
    }

    private Fixture fixture(int correctAnswers) {
        UUID userId = UUID.randomUUID();
        UUID weakTopicId = UUID.randomUUID();
        UUID quizId = UUID.randomUUID();
        UUID roadmapId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UserAccount user = mock(UserAccount.class);
        Roadmap roadmap = mock(Roadmap.class);
        RoadmapVersion version = mock(RoadmapVersion.class);
        RoadmapItem item = mock(RoadmapItem.class);
        when(user.getEmail()).thenReturn("learner@example.com");
        when(user.getId()).thenReturn(userId);
        when(roadmap.getId()).thenReturn(roadmapId);
        when(version.getId()).thenReturn(versionId);
        when(version.getRoadmap()).thenReturn(roadmap);
        when(item.getId()).thenReturn(itemId);
        when(item.getItemType()).thenReturn(RoadmapItemType.LEARNING_UNIT);
        when(item.getRoadmapVersion()).thenReturn(version);
        WeakTopic weakTopic = WeakTopic.create(
                user, roadmap, version, item, WeakTopicTrigger.LOW_RATING,
                null, 2, Instant.parse("2026-09-01T00:00:00Z"), "UTC");
        ReflectionTestUtils.setField(weakTopic, "id", weakTopicId);

        Quiz quiz = Quiz.createMasteryCheck(user, roadmap, version, weakTopic);
        ReflectionTestUtils.setField(quiz, "id", quizId);
        List<AnswerSubmissionDto> answers = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            QuizQuestion question = QuizQuestion.create(
                    item, "Question " + index, "[]", "A", "Explanation", index);
            UUID questionId = UUID.randomUUID();
            ReflectionTestUtils.setField(question, "id", questionId);
            quiz.addQuestion(question);
            answers.add(new AnswerSubmissionDto(
                    questionId,
                    index < correctAnswers ? "A" : "B"));
        }

        AtomicReference<QuizAttempt> persisted = new AtomicReference<>();
        when(quizRepository.findWithQuestionsByIdAndUserIdForUpdate(quizId, userId))
                .thenReturn(Optional.of(quiz));
        when(weakTopicRepository.findByIdAndUserIdForUpdate(weakTopicId, userId))
                .thenReturn(Optional.of(weakTopic));
        when(quizAttemptRepository.saveAndFlush(any(QuizAttempt.class)))
                .thenAnswer(invocation -> {
                    QuizAttempt attempt = invocation.getArgument(0);
                    ReflectionTestUtils.setField(attempt, "id", UUID.randomUUID());
                    persisted.set(attempt);
                    return attempt;
                });
        lenient().when(quizAttemptRepository.findFirstByQuizIdAndUserIdOrderByAttemptNumberDesc(
                quizId, userId))
                .thenAnswer(ignored -> Optional.ofNullable(persisted.get()));

        return new Fixture(
                userId,
                weakTopicId,
                quizId,
                weakTopic,
                new SubmitQuizRequest(answers));
    }

    private record Fixture(
            UUID userId,
            UUID weakTopicId,
            UUID quizId,
            WeakTopic weakTopic,
            SubmitQuizRequest request) {}
}
