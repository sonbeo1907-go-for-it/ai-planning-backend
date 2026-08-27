package com.codegym.aiplanning.service.evaluation.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.evaluation.dto.AnswerSubmissionDto;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.evaluation.WeakTopic;
import com.codegym.aiplanning.entity.evaluation.WeakTopicStatus;
import com.codegym.aiplanning.entity.evaluation.WeakTopicTrigger;
import com.codegym.aiplanning.entity.evaluation.Quiz;
import com.codegym.aiplanning.entity.evaluation.QuizQuestion;
import com.codegym.aiplanning.entity.evaluation.QuizStatus;
import com.codegym.aiplanning.entity.evaluation.QuizType;
import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapItem;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.repository.evaluation.WeakTopicRepository;
import com.codegym.aiplanning.repository.evaluation.QuizRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapItemRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapRepository;
import com.codegym.aiplanning.service.evaluation.WeakTopicContextResolver.WeakTopicPromptContext;
import com.codegym.aiplanning.service.evaluation.QuizGeneratorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeakTopicServiceImplTest {

    @Mock
    private WeakTopicRepository weakTopicRepository;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private RoadmapRepository roadmapRepository;
    @Mock
    private RoadmapItemRepository roadmapItemRepository;
    @Mock
    private QuizRepository quizRepository;
    @Mock
    private QuizGeneratorService quizGeneratorService;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private WeakTopicServiceImpl weakTopicService;

    private UUID userId;
    private UUID roadmapId;
    private UUID roadmapItemId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        roadmapId = UUID.randomUUID();
        roadmapItemId = UUID.randomUUID();
    }

    @Test
    void processEvaluationResult_highScoreAndHighRating_doesNothing() {
        weakTopicService.processEvaluationResult(userId, roadmapId, roadmapItemId, new BigDecimal("85.00"), 3);

        verify(weakTopicRepository, never()).findByUserIdAndRoadmapItemId(any(), any());
        verify(weakTopicRepository, never()).save(any());
    }

    @Test
    void processEvaluationResult_nullScoreAndHighRating_doesNothing() {
        weakTopicService.processEvaluationResult(userId, roadmapId, roadmapItemId, null, 4);

        verify(weakTopicRepository, never()).save(any());
    }

    @Test
    void processEvaluationResult_highScoreAndNullRating_doesNothing() {
        weakTopicService.processEvaluationResult(userId, roadmapId, roadmapItemId, new BigDecimal("90.00"), null);

        verify(weakTopicRepository, never()).save(any());
    }

    @Test
    void processEvaluationResult_lowScoreHighRating_createsQuizFailedTrigger() {
        setupMockEntities();
        when(weakTopicRepository.findByUserIdAndRoadmapItemId(userId, roadmapItemId)).thenReturn(Optional.empty());

        weakTopicService.processEvaluationResult(userId, roadmapId, roadmapItemId, new BigDecimal("79.99"), 3);

        ArgumentCaptor<WeakTopic> captor = ArgumentCaptor.forClass(WeakTopic.class);
        verify(weakTopicRepository).save(captor.capture());
        
        WeakTopic saved = captor.getValue();
        assertEquals(WeakTopicTrigger.QUIZ_FAILED, saved.getTriggerSource());
        assertEquals(new BigDecimal("79.99"), saved.getLastQuizScore());
        assertEquals(3, saved.getLastUnderstandingRating());
        assertEquals(WeakTopicStatus.UNRESOLVED, saved.getStatus());
    }

    @Test
    void processEvaluationResult_highScoreLowRating_createsLowRatingTrigger() {
        setupMockEntities();
        when(weakTopicRepository.findByUserIdAndRoadmapItemId(userId, roadmapItemId)).thenReturn(Optional.empty());

        weakTopicService.processEvaluationResult(userId, roadmapId, roadmapItemId, new BigDecimal("90.00"), 2);

        ArgumentCaptor<WeakTopic> captor = ArgumentCaptor.forClass(WeakTopic.class);
        verify(weakTopicRepository).save(captor.capture());
        
        WeakTopic saved = captor.getValue();
        assertEquals(WeakTopicTrigger.LOW_RATING, saved.getTriggerSource());
        assertEquals(new BigDecimal("90.00"), saved.getLastQuizScore());
        assertEquals(2, saved.getLastUnderstandingRating());
        assertEquals(WeakTopicStatus.UNRESOLVED, saved.getStatus());
    }

    @Test
    void processEvaluationResult_lowScoreLowRating_createsBothTrigger() {
        setupMockEntities();
        when(weakTopicRepository.findByUserIdAndRoadmapItemId(userId, roadmapItemId)).thenReturn(Optional.empty());

        weakTopicService.processEvaluationResult(userId, roadmapId, roadmapItemId, new BigDecimal("60.00"), 1);

        ArgumentCaptor<WeakTopic> captor = ArgumentCaptor.forClass(WeakTopic.class);
        verify(weakTopicRepository).save(captor.capture());
        
        WeakTopic saved = captor.getValue();
        assertEquals(WeakTopicTrigger.BOTH, saved.getTriggerSource());
        assertEquals(WeakTopicStatus.UNRESOLVED, saved.getStatus());
    }

    @Test
    void processEvaluationResult_existingWeakTopic_updatesTriggerAndSaves() {
        WeakTopic existingTopic = mock(WeakTopic.class);
        when(weakTopicRepository.findByUserIdAndRoadmapItemId(userId, roadmapItemId)).thenReturn(Optional.of(existingTopic));

        weakTopicService.processEvaluationResult(userId, roadmapId, roadmapItemId, new BigDecimal("50.00"), 1);

        verify(existingTopic).updateTrigger(eq(WeakTopicTrigger.BOTH), eq(new BigDecimal("50.00")), eq(1), any(Instant.class));
        verify(weakTopicRepository).save(existingTopic);
        // Entity fetch should not happen
        verify(userAccountRepository, never()).findById(any());
    }

    @Test
    void processEvaluationResult_userNotFound_throwsException() {
        when(weakTopicRepository.findByUserIdAndRoadmapItemId(userId, roadmapItemId)).thenReturn(Optional.empty());
        when(userAccountRepository.findById(userId)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, 
            () -> weakTopicService.processEvaluationResult(userId, roadmapId, roadmapItemId, new BigDecimal("70.00"), 3));
            
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.errorCode());
    }

    @Test
    void resolveUnresolvedWeakTopics_returnsMappedContexts() {
        WeakTopic mockTopic = mock(WeakTopic.class);
        RoadmapItem mockItem = mock(RoadmapItem.class);
        RoadmapItem mockParent = mock(RoadmapItem.class);
        
        UUID topicId = UUID.randomUUID();
        when(mockTopic.getId()).thenReturn(topicId);
        when(mockTopic.getRoadmapItem()).thenReturn(mockItem);
        when(mockTopic.getLastUnderstandingRating()).thenReturn(2);
        when(mockTopic.getLastQuizScore()).thenReturn(new BigDecimal("75.50"));
        
        when(mockItem.getId()).thenReturn(roadmapItemId);
        when(mockItem.getTitle()).thenReturn("Child Item");
        when(mockItem.getParent()).thenReturn(mockParent);
        when(mockParent.getTitle()).thenReturn("Parent Milestone");

        when(weakTopicRepository.findWithItemByUserIdAndRoadmapIdAndStatusIn(
                eq(userId), eq(roadmapId), eq(Collections.singletonList(WeakTopicStatus.UNRESOLVED))))
                .thenReturn(List.of(mockTopic));

        List<WeakTopicPromptContext> result = weakTopicService.resolveUnresolvedWeakTopics(userId, roadmapId);

        assertEquals(1, result.size());
        WeakTopicPromptContext ctx = result.get(0);
        assertEquals(topicId, ctx.weakTopicId());
        assertEquals(roadmapItemId, ctx.roadmapItemId());
        assertEquals("Child Item", ctx.topicTitle());
        assertEquals("Parent Milestone", ctx.milestoneTitle());
        assertEquals(2, ctx.lastRating());
        assertEquals(75.50, ctx.lastScore());
    }

    @Test
    void resolveUnresolvedWeakTopics_noParent_mapsMilestoneToNull() {
        WeakTopic mockTopic = mock(WeakTopic.class);
        RoadmapItem mockItem = mock(RoadmapItem.class);
        
        when(mockTopic.getRoadmapItem()).thenReturn(mockItem);
        when(mockTopic.getLastQuizScore()).thenReturn(null);
        when(mockTopic.getLastUnderstandingRating()).thenReturn(null);
        
        when(mockItem.getParent()).thenReturn(null);

        when(weakTopicRepository.findWithItemByUserIdAndRoadmapIdAndStatusIn(
                eq(userId), eq(roadmapId), eq(Collections.singletonList(WeakTopicStatus.UNRESOLVED))))
                .thenReturn(List.of(mockTopic));

        List<WeakTopicPromptContext> result = weakTopicService.resolveUnresolvedWeakTopics(userId, roadmapId);

        assertEquals(1, result.size());
        assertNull(result.get(0).milestoneTitle());
        assertNull(result.get(0).lastScore());
        assertNull(result.get(0).lastRating());
    }

    @Test
    void submitMasteryCheck_allQuestionsCorrect_marksTopicMastered() {
        WeakTopic weakTopic = mock(WeakTopic.class);
        Quiz quiz = mock(Quiz.class);
        QuizQuestion question = mock(QuizQuestion.class);
        UUID quizId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();

        when(weakTopicRepository.findByIdAndUserId(roadmapItemId, userId)).thenReturn(Optional.of(weakTopic));
        when(quizRepository.findWithQuestionsByIdAndUserId(quizId, userId)).thenReturn(Optional.of(quiz));
        when(quiz.getQuizType()).thenReturn(QuizType.MASTERY_CHECK);
        when(quiz.getTargetWeakTopic()).thenReturn(weakTopic);
        when(weakTopic.getId()).thenReturn(roadmapItemId);
        when(quiz.getStatus()).thenReturn(QuizStatus.GENERATED);
        when(quiz.getQuestions()).thenReturn(List.of(question));
        when(question.getId()).thenReturn(questionId);
        when(question.getIsCorrect()).thenReturn(true);
        when(weakTopic.getStatus()).thenReturn(WeakTopicStatus.MASTERED);

        MasteryCheckResultResponse result = weakTopicService.submitMasteryCheck(
                userId,
                roadmapItemId,
                quizId,
                new SubmitQuizRequest(List.of(new AnswerSubmissionDto(questionId, "A"))));

        assertEquals(new BigDecimal("100.00"), result.quizScore());
        assertEquals(true, result.isMastered());
        verify(question).answer("A");
        verify(weakTopic).markMastered(any(Instant.class));
        verify(weakTopicRepository).save(weakTopic);
    }

    @Test
    void submitMasteryCheck_withWrongAnswer_doesNotMarkTopicMastered() {
        WeakTopic weakTopic = mock(WeakTopic.class);
        Quiz quiz = mock(Quiz.class);
        QuizQuestion question = mock(QuizQuestion.class);
        UUID quizId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();

        when(weakTopicRepository.findByIdAndUserId(roadmapItemId, userId)).thenReturn(Optional.of(weakTopic));
        when(quizRepository.findWithQuestionsByIdAndUserId(quizId, userId)).thenReturn(Optional.of(quiz));
        when(quiz.getQuizType()).thenReturn(QuizType.MASTERY_CHECK);
        when(quiz.getTargetWeakTopic()).thenReturn(weakTopic);
        when(weakTopic.getId()).thenReturn(roadmapItemId);
        when(quiz.getStatus()).thenReturn(QuizStatus.GENERATED);
        when(quiz.getQuestions()).thenReturn(List.of(question));
        when(question.getId()).thenReturn(questionId);
        when(question.getIsCorrect()).thenReturn(false);
        when(weakTopic.getStatus()).thenReturn(WeakTopicStatus.UNRESOLVED);

        MasteryCheckResultResponse result = weakTopicService.submitMasteryCheck(
                userId,
                roadmapItemId,
                quizId,
                new SubmitQuizRequest(List.of(new AnswerSubmissionDto(questionId, "B"))));

        assertEquals(new BigDecimal("0.00"), result.quizScore());
        assertEquals(false, result.isMastered());
        verify(weakTopic, never()).markMastered(any(Instant.class));
        verify(weakTopicRepository, never()).save(weakTopic);
    }

    @Test
    void submitMasteryCheck_withDailyQuiz_rejectsQuiz() {
        WeakTopic weakTopic = mock(WeakTopic.class);
        Quiz quiz = mock(Quiz.class);
        UUID quizId = UUID.randomUUID();

        when(weakTopicRepository.findByIdAndUserId(roadmapItemId, userId)).thenReturn(Optional.of(weakTopic));
        when(quizRepository.findWithQuestionsByIdAndUserId(quizId, userId)).thenReturn(Optional.of(quiz));
        when(quiz.getQuizType()).thenReturn(QuizType.DAILY_MICRO_QUIZ);

        BusinessException exception = assertThrows(BusinessException.class, () -> weakTopicService.submitMasteryCheck(
                userId, roadmapItemId, quizId, new SubmitQuizRequest(Collections.emptyList())));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.errorCode());
    }

    private void setupMockEntities() {
        when(userAccountRepository.findById(userId)).thenReturn(Optional.of(mock(UserAccount.class)));
        when(roadmapRepository.findById(roadmapId)).thenReturn(Optional.of(mock(Roadmap.class)));
        when(roadmapItemRepository.findById(roadmapItemId)).thenReturn(Optional.of(mock(RoadmapItem.class)));
    }
}
