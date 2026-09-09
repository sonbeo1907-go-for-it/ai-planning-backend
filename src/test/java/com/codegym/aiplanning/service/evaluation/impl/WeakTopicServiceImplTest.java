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
import com.codegym.aiplanning.controller.evaluation.dto.WeakTopicResponse;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.evaluation.WeakTopic;
import com.codegym.aiplanning.entity.evaluation.WeakTopicStatus;
import com.codegym.aiplanning.entity.evaluation.WeakTopicTrigger;
import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapItem;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersion;
import com.codegym.aiplanning.entity.roadmap.RoadmapItemType;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.repository.evaluation.WeakTopicRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapItemRepository;
import com.codegym.aiplanning.repository.evaluation.QuizRepository;
import com.codegym.aiplanning.service.evaluation.QuizGeneratorService;
import com.codegym.aiplanning.repository.roadmap.RoadmapRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.codegym.aiplanning.service.evaluation.WeakTopicContextResolver.WeakTopicPromptContext;
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
    private RoadmapItemRepository roadmapItemRepository;
    @Mock
    private RoadmapRepository roadmapRepository;
    @Mock
    private QuizRepository quizRepository;
    @Mock
    private QuizGeneratorService quizGeneratorService;
    @Mock
    private DailyEvaluationPersistenceService evaluationPersistenceService;
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
        Roadmap existingRoadmap = mock(Roadmap.class);
        RoadmapItem existingLearningUnit = mock(RoadmapItem.class);
        when(existingRoadmap.getId()).thenReturn(roadmapId);
        when(existingTopic.getRoadmap()).thenReturn(existingRoadmap);
        when(existingTopic.getRoadmapItem()).thenReturn(existingLearningUnit);
        stubLearningUnitHierarchy(existingLearningUnit);
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
    void processEvaluationResult_topicTarget_rejectsAmbiguousWeakness() {
        Roadmap roadmap = mock(Roadmap.class);
        RoadmapVersion version = mock(RoadmapVersion.class);
        RoadmapItem topic = mock(RoadmapItem.class);
        when(weakTopicRepository.findByUserIdAndRoadmapItemId(userId, roadmapItemId))
                .thenReturn(Optional.empty());
        when(userAccountRepository.findById(userId))
                .thenReturn(Optional.of(mock(UserAccount.class)));
        when(roadmapItemRepository.findOwnedById(roadmapItemId, userId))
                .thenReturn(Optional.of(topic));
        when(topic.getRoadmapVersion()).thenReturn(version);
        when(topic.getItemType()).thenReturn(RoadmapItemType.TOPIC);
        when(version.getRoadmap()).thenReturn(roadmap);
        when(roadmap.getId()).thenReturn(roadmapId);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> weakTopicService.processEvaluationResult(
                        userId,
                        roadmapId,
                        roadmapItemId,
                        new BigDecimal("70.00"),
                        null));

        assertEquals(ErrorCode.ROADMAP_STRUCTURE_INCOMPLETE, exception.errorCode());
        verify(weakTopicRepository, never()).save(any());
    }

    @Test
    void resolveUnresolvedWeakTopics_returnsMappedContexts() {
        WeakTopic mockTopic = mock(WeakTopic.class);
        RoadmapItem mockItem = mock(RoadmapItem.class);
        RoadmapItem mockParentTopic = mock(RoadmapItem.class);
        RoadmapItem mockMilestone = mock(RoadmapItem.class);
        
        UUID weakTopicId = UUID.randomUUID();
        UUID parentTopicId = UUID.randomUUID();
        UUID milestoneId = UUID.randomUUID();
        when(mockTopic.getId()).thenReturn(weakTopicId);
        when(mockTopic.getRoadmapItem()).thenReturn(mockItem);
        when(mockTopic.getLastUnderstandingRating()).thenReturn(2);
        when(mockTopic.getLastQuizScore()).thenReturn(new BigDecimal("75.50"));
        
        when(mockItem.getId()).thenReturn(roadmapItemId);
        when(mockItem.getItemType()).thenReturn(RoadmapItemType.LEARNING_UNIT);
        when(mockItem.getTitle()).thenReturn("Encapsulation exercise");
        when(mockItem.getParent()).thenReturn(mockParentTopic);
        when(mockParentTopic.getId()).thenReturn(parentTopicId);
        when(mockParentTopic.getTitle()).thenReturn("Core OOP principles");
        when(mockParentTopic.getParent()).thenReturn(mockMilestone);
        when(mockMilestone.getId()).thenReturn(milestoneId);
        when(mockMilestone.getTitle()).thenReturn("OOP foundations");

        when(weakTopicRepository.findWithItemByUserIdAndRoadmapVersionIdAndStatusIn(
                eq(userId), eq(roadmapId), eq(Collections.singletonList(WeakTopicStatus.UNRESOLVED))))
                .thenReturn(List.of(mockTopic));

        List<WeakTopicPromptContext> result = weakTopicService.resolveUnresolvedWeakTopics(userId, roadmapId);

        assertEquals(1, result.size());
        WeakTopicPromptContext ctx = result.get(0);
        assertEquals(weakTopicId, ctx.weakTopicId());
        assertEquals(roadmapItemId, ctx.targetItemId());
        assertEquals(RoadmapItemType.LEARNING_UNIT, ctx.targetItemType());
        assertEquals(roadmapItemId, ctx.learningUnitId());
        assertEquals("Encapsulation exercise", ctx.learningUnitTitle());
        assertEquals(parentTopicId, ctx.topicId());
        assertEquals("Core OOP principles", ctx.topicTitle());
        assertEquals(milestoneId, ctx.milestoneId());
        assertEquals("OOP foundations", ctx.milestoneTitle());
        assertEquals(2, ctx.lastRating());
        assertEquals(75.50, ctx.lastScore());
    }

    @Test
    void resolveUnresolvedWeakTopics_legacyTopic_remainsReadable() {
        WeakTopic mockTopic = mock(WeakTopic.class);
        RoadmapItem mockItem = mock(RoadmapItem.class);
        
        when(mockTopic.getRoadmapItem()).thenReturn(mockItem);
        when(mockTopic.getLastQuizScore()).thenReturn(null);
        when(mockTopic.getLastUnderstandingRating()).thenReturn(null);
        
        when(mockItem.getParent()).thenReturn(null);
        when(mockItem.getItemType()).thenReturn(RoadmapItemType.TOPIC);
        when(mockItem.getId()).thenReturn(roadmapItemId);
        when(mockItem.getTitle()).thenReturn("Legacy topic");

        when(weakTopicRepository.findWithItemByUserIdAndRoadmapVersionIdAndStatusIn(
                eq(userId), eq(roadmapId), eq(Collections.singletonList(WeakTopicStatus.UNRESOLVED))))
                .thenReturn(List.of(mockTopic));

        List<WeakTopicPromptContext> result = weakTopicService.resolveUnresolvedWeakTopics(userId, roadmapId);

        assertEquals(1, result.size());
        assertNull(result.get(0).learningUnitId());
        assertEquals(roadmapItemId, result.get(0).topicId());
        assertEquals("Legacy topic", result.get(0).topicTitle());
        assertNull(result.get(0).milestoneTitle());
        assertNull(result.get(0).lastScore());
        assertNull(result.get(0).lastRating());
    }

    @Test
    void getWeakTopics_ownedRoadmap_returnsOwnerScopedTopics() {
        Roadmap roadmap = mock(Roadmap.class);
        when(roadmapRepository.findByIdAndOwnerId(roadmapId, userId))
                .thenReturn(Optional.of(roadmap));
        when(weakTopicRepository.findWithItemByUserIdAndRoadmapIdAndStatusIn(
                eq(userId),
                eq(roadmapId),
                eq(List.of(WeakTopicStatus.values()))))
                .thenReturn(List.of());

        List<WeakTopicResponse> result = weakTopicService.getWeakTopics(
                userId,
                roadmapId,
                Collections.emptySet());

        assertEquals(List.of(), result);
        verify(weakTopicRepository).findWithItemByUserIdAndRoadmapIdAndStatusIn(
                userId,
                roadmapId,
                List.of(WeakTopicStatus.values()));
    }

    @Test
    void getWeakTopics_unownedRoadmap_throwsWithoutQueryingWeakTopics() {
        when(roadmapRepository.findByIdAndOwnerId(roadmapId, userId))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> weakTopicService.getWeakTopics(
                        userId,
                        roadmapId,
                        Collections.emptySet()));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.errorCode());
        verify(weakTopicRepository, never())
                .findWithItemByUserIdAndRoadmapIdAndStatusIn(any(), any(), any());
    }

    @Test
    void getWeakTopics_learningUnitTarget_exposesUnambiguousHierarchy() {
        UUID weakTopicId = UUID.randomUUID();
        UUID roadmapVersionId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID milestoneId = UUID.randomUUID();
        Roadmap roadmap = mock(Roadmap.class);
        RoadmapVersion roadmapVersion = mock(RoadmapVersion.class);
        RoadmapItem learningUnit = mock(RoadmapItem.class);
        RoadmapItem topic = mock(RoadmapItem.class);
        RoadmapItem milestone = mock(RoadmapItem.class);
        WeakTopic weakTopic = mock(WeakTopic.class);

        when(roadmap.getId()).thenReturn(roadmapId);
        when(roadmapVersion.getId()).thenReturn(roadmapVersionId);
        when(learningUnit.getId()).thenReturn(roadmapItemId);
        when(learningUnit.getItemType()).thenReturn(RoadmapItemType.LEARNING_UNIT);
        when(learningUnit.getTitle()).thenReturn("Encapsulation exercise");
        when(learningUnit.getParent()).thenReturn(topic);
        when(topic.getId()).thenReturn(topicId);
        when(topic.getTitle()).thenReturn("Core OOP principles");
        when(topic.getParent()).thenReturn(milestone);
        when(milestone.getId()).thenReturn(milestoneId);
        when(milestone.getTitle()).thenReturn("OOP foundations");
        when(weakTopic.getId()).thenReturn(weakTopicId);
        when(weakTopic.getRoadmap()).thenReturn(roadmap);
        when(weakTopic.getRoadmapVersion()).thenReturn(roadmapVersion);
        when(weakTopic.getRoadmapItem()).thenReturn(learningUnit);
        when(weakTopic.getStatus()).thenReturn(WeakTopicStatus.UNRESOLVED);
        when(weakTopic.getTriggerSource()).thenReturn(WeakTopicTrigger.QUIZ_FAILED);
        when(roadmapRepository.findByIdAndOwnerId(roadmapId, userId))
                .thenReturn(Optional.of(roadmap));
        when(weakTopicRepository.findWithItemByUserIdAndRoadmapIdAndStatusIn(
                userId,
                roadmapId,
                List.of(WeakTopicStatus.values())))
                .thenReturn(List.of(weakTopic));

        WeakTopicResponse response = weakTopicService.getWeakTopics(
                        userId,
                        roadmapId,
                        Collections.emptySet())
                .get(0);

        assertEquals(roadmapItemId, response.roadmapItemId());
        assertEquals(RoadmapItemType.LEARNING_UNIT, response.targetItemType());
        assertEquals(roadmapItemId, response.learningUnitId());
        assertEquals("Encapsulation exercise", response.learningUnitTitle());
        assertEquals(topicId, response.topicId());
        assertEquals("Core OOP principles", response.topicTitle());
        assertEquals(milestoneId, response.milestoneId());
        assertEquals("OOP foundations", response.milestoneTitle());
    }

    private void setupMockEntities() {
        Roadmap roadmap = mock(Roadmap.class);
        RoadmapVersion version = mock(RoadmapVersion.class);
        RoadmapItem item = mock(RoadmapItem.class);
        when(roadmap.getId()).thenReturn(roadmapId);
        when(version.getRoadmap()).thenReturn(roadmap);
        when(item.getRoadmapVersion()).thenReturn(version);
        stubLearningUnitHierarchy(item, version);
        when(userAccountRepository.findById(userId)).thenReturn(Optional.of(mock(UserAccount.class)));
        when(roadmapItemRepository.findOwnedById(roadmapItemId, userId)).thenReturn(Optional.of(item));
    }

    private void stubLearningUnitHierarchy(RoadmapItem learningUnit) {
        stubLearningUnitHierarchy(learningUnit, mock(RoadmapVersion.class));
    }

    private void stubLearningUnitHierarchy(
            RoadmapItem learningUnit,
            RoadmapVersion version) {
        RoadmapItem topic = mock(RoadmapItem.class);
        RoadmapItem milestone = mock(RoadmapItem.class);
        UUID versionId = UUID.randomUUID();
        when(version.getId()).thenReturn(versionId);
        when(learningUnit.getRoadmapVersion()).thenReturn(version);
        when(learningUnit.getItemType()).thenReturn(RoadmapItemType.LEARNING_UNIT);
        when(learningUnit.getParent()).thenReturn(topic);
        when(topic.getItemType()).thenReturn(RoadmapItemType.TOPIC);
        when(topic.getRoadmapVersion()).thenReturn(version);
        when(topic.getParent()).thenReturn(milestone);
        when(milestone.getItemType()).thenReturn(RoadmapItemType.MILESTONE);
        when(milestone.getRoadmapVersion()).thenReturn(version);
    }
}
